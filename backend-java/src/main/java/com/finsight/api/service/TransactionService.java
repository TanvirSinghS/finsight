package com.finsight.api.service;

import com.finsight.api.domain.*;
import com.finsight.api.dto.TransactionDtos.*;
import com.finsight.api.exception.ApiException;
import com.finsight.api.repository.AccountRepository;
import com.finsight.api.repository.CategoryRepository;
import com.finsight.api.repository.TransactionRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final UserService userService;
    private final CurrencyService currencyService;

    public TransactionService(TransactionRepository transactionRepository, AccountRepository accountRepository,
                              CategoryRepository categoryRepository, UserService userService,
                              CurrencyService currencyService) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.userService = userService;
        this.currencyService = currencyService;
    }

    public record TransactionFilter(TransactionType type, UUID accountId, UUID categoryId, LocalDate from,
                                    LocalDate to, String search, BigDecimal minAmount, BigDecimal maxAmount) {
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(UUID userId, TransactionFilter filter, int page, int size,
                                                  String sortBy, String direction) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String field = switch (sortBy == null ? "occurredOn" : sortBy) {
            case "amount" -> "amount";
            case "description" -> "description";
            case "createdAt" -> "createdAt";
            case "type" -> "type";
            default -> "occurredOn";
        };
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(dir, field).and(Sort.by(Sort.Direction.DESC, "createdAt")));

        Specification<Transaction> spec = buildSpec(userId, filter);
        Page<Transaction> result = transactionRepository.findAll(spec, pageable);
        return new PageResponse<>(result.getContent().stream().map(TransactionService::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listAll(UUID userId, TransactionFilter filter) {
        return transactionRepository.findAll(buildSpec(userId, filter),
                        Sort.by(Sort.Direction.DESC, "occurredOn"))
                .stream().map(TransactionService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID userId, UUID id) {
        return toResponse(findOwned(userId, id));
    }

    @Transactional
    public TransactionResponse create(UUID userId, TransactionRequest request) {
        User user = userService.get(userId);
        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> ApiException.badRequest("Select one of your own accounts."));
        Category category = resolveCategory(userId, request.categoryId(), request.type());
        String currency = normalizeCurrency(request.currency(), user.getBaseCurrency());
        BigDecimal rate = currencyService.rate(currency, user.getBaseCurrency());

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setAccount(account);
        transaction.setCategory(category);
        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        transaction.setCurrency(currency);
        transaction.setExchangeRate(rate);
        transaction.setAmountBase(request.amount().multiply(rate).setScale(2, RoundingMode.HALF_UP));
        transaction.setDescription(request.description().trim());
        transaction.setMerchant(trimToNull(request.merchant()));
        transaction.setNotes(trimToNull(request.notes()));
        transaction.setOccurredOn(request.occurredOn());
        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponse update(UUID userId, UUID id, TransactionRequest request) {
        Transaction transaction = findOwned(userId, id);
        User user = transaction.getUser();
        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> ApiException.badRequest("Select one of your own accounts."));
        Category category = resolveCategory(userId, request.categoryId(), request.type());
        String currency = normalizeCurrency(request.currency(), user.getBaseCurrency());
        BigDecimal rate = currencyService.rate(currency, user.getBaseCurrency());

        transaction.setAccount(account);
        transaction.setCategory(category);
        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        transaction.setCurrency(currency);
        transaction.setExchangeRate(rate);
        transaction.setAmountBase(request.amount().multiply(rate).setScale(2, RoundingMode.HALF_UP));
        transaction.setDescription(request.description().trim());
        transaction.setMerchant(trimToNull(request.merchant()));
        transaction.setNotes(trimToNull(request.notes()));
        transaction.setOccurredOn(request.occurredOn());
        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        transactionRepository.delete(findOwned(userId, id));
    }

    @Transactional(readOnly = true)
    public String exportCsv(UUID userId, TransactionFilter filter) {
        List<Transaction> rows = transactionRepository.findAll(buildSpec(userId, filter),
                Sort.by(Sort.Direction.ASC, "occurredOn"));
        StringWriter writer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                .setHeader("Date", "Type", "Description", "Merchant", "Category", "Account", "Amount",
                        "Currency", "Amount (base)")
                .build())) {
            for (Transaction t : rows) {
                printer.printRecord(t.getOccurredOn(), t.getType(), t.getDescription(),
                        t.getMerchant() == null ? "" : t.getMerchant(),
                        t.getCategory() == null ? "Uncategorized" : t.getCategory().getName(),
                        t.getAccount().getName(), t.getAmount(), t.getCurrency(), t.getAmountBase());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not build CSV", ex);
        }
        return writer.toString();
    }

    @Transactional
    public CsvImportResult importCsv(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Upload a non-empty CSV file.");
        }
        User user = userService.get(userId);
        List<Account> accounts = accountRepository.findByUserIdAndArchivedFalseOrderByName(userId);
        if (accounts.isEmpty()) {
            throw ApiException.badRequest("Create an account before importing transactions.");
        }
        List<Category> categories = categoryRepository.findByUserIdOrderByKindAscNameAsc(userId);

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVParser.parse(reader, CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build())) {

            Map<String, Integer> headerMap = parser.getHeaderMap();
            List<String> lowerHeaders = headerMap.keySet().stream().map(String::toLowerCase).toList();
            if (!lowerHeaders.contains("date") || !lowerHeaders.contains("amount")
                    || !lowerHeaders.contains("description")) {
                throw ApiException.badRequest("CSV needs at least Date, Amount and Description columns.");
            }

            int rowNumber = 1;
            for (CSVRecord record : parser) {
                rowNumber++;
                if (imported >= 5000) {
                    errors.add("Import capped at 5000 rows.");
                    break;
                }
                try {
                    String dateRaw = value(record, "date");
                    String amountRaw = value(record, "amount").replace(",", "").replace("$", "");
                    String description = value(record, "description");
                    if (dateRaw.isBlank() || amountRaw.isBlank() || description.isBlank()) {
                        throw new IllegalArgumentException("date, amount and description are required");
                    }
                    TransactionType type = value(record, "type").toUpperCase(Locale.ROOT).contains("INC")
                            ? TransactionType.INCOME : TransactionType.EXPENSE;
                    BigDecimal amount = new BigDecimal(amountRaw).abs();
                    if (amount.signum() <= 0) {
                        throw new IllegalArgumentException("amount must be positive");
                    }
                    LocalDate occurredOn = LocalDate.parse(dateRaw);
                    String categoryName = value(record, "category");
                    CategoryKind kind = type == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
                    Category category = categories.stream()
                            .filter(c -> c.getKind() == kind && c.getName().equalsIgnoreCase(categoryName))
                            .findFirst().orElseThrow(() -> new IllegalArgumentException(
                                    "unknown category '" + categoryName + "'"));
                    String accountName = value(record, "account");
                    Account account = accounts.stream()
                            .filter(a -> a.getName().equalsIgnoreCase(accountName))
                            .findFirst().orElse(accounts.get(0));
                    String currency = normalizeCurrency(value(record, "currency"), user.getBaseCurrency());
                    String key = dateRaw + "|" + amountRaw + "|" + description.trim().toLowerCase(Locale.ROOT);
                    if (!seen.add(key)) {
                        skipped++;
                        continue;
                    }
                    BigDecimal rate = currencyService.rate(currency, user.getBaseCurrency());
                    Transaction transaction = new Transaction();
                    transaction.setUser(user);
                    transaction.setAccount(account);
                    transaction.setCategory(category);
                    transaction.setType(type);
                    transaction.setAmount(amount);
                    transaction.setCurrency(currency);
                    transaction.setExchangeRate(rate);
                    transaction.setAmountBase(amount.multiply(rate).setScale(2, RoundingMode.HALF_UP));
                    transaction.setDescription(description);
                    transaction.setMerchant(trimToNull(value(record, "merchant")));
                    transaction.setOccurredOn(occurredOn);
                    transactionRepository.save(transaction);
                    imported++;
                } catch (IllegalArgumentException ex) {
                    skipped++;
                    if (errors.size() < 10) {
                        errors.add("Row " + rowNumber + ": " + ex.getMessage());
                    }
                }
            }
        } catch (IOException ex) {
            throw ApiException.badRequest("Could not read CSV: " + ex.getMessage());
        }
        return new CsvImportResult(imported, skipped, errors);
    }

    public static TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(t.getId(), t.getType(), t.getAmount(), t.getCurrency(),
                t.getExchangeRate(), t.getAmountBase(), t.getDescription(), t.getMerchant(), t.getNotes(),
                t.getOccurredOn(), t.getAccount().getId(), t.getAccount().getName(),
                t.getCategory() == null ? null : t.getCategory().getId(),
                t.getCategory() == null ? "Uncategorized" : t.getCategory().getName(),
                t.getCategory() == null ? "#94a3b8" : t.getCategory().getColor(), t.getRecurringId());
    }

    private Specification<Transaction> buildSpec(UUID userId, TransactionFilter filter) {
        Specification<Transaction> spec = (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
        if (filter == null) {
            return spec;
        }
        if (filter.type() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), filter.type()));
        }
        if (filter.accountId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("account").get("id"), filter.accountId()));
        }
        if (filter.categoryId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), filter.categoryId()));
        }
        if (filter.from() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredOn"), filter.from()));
        }
        if (filter.to() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("occurredOn"), filter.to()));
        }
        if (filter.minAmount() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amountBase"), filter.minAmount()));
        }
        if (filter.maxAmount() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("amountBase"), filter.maxAmount()));
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            String term = "%" + filter.search().trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("description")), term),
                    cb.like(cb.lower(root.get("merchant")), term)));
        }
        return spec;
    }

    private Transaction findOwned(UUID userId, UUID id) {
        return transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Transaction not found."));
    }

    private Category resolveCategory(UUID userId, UUID categoryId, TransactionType type) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> ApiException.badRequest("Category not found."));
        CategoryKind expected = type == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
        if (category.getKind() != expected) {
            throw ApiException.badRequest("That category is for " + category.getKind().name().toLowerCase()
                    + " transactions.");
        }
        return category;
    }

    private String normalizeCurrency(String currency, String fallback) {
        return currency == null || currency.isBlank() ? fallback : currency.toUpperCase(Locale.ROOT);
    }

    private String value(CSVRecord record, String header) {
        try {
            String raw = record.get(header);
            return raw == null ? "" : raw.trim();
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
