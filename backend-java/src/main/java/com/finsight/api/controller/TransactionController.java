package com.finsight.api.controller;

import com.finsight.api.domain.TransactionType;
import com.finsight.api.dto.TransactionDtos.*;
import com.finsight.api.service.TransactionService;
import com.finsight.api.service.TransactionService.TransactionFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "CRUD, filtering, export and CSV import")
public class TransactionController {
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    @Operation(summary = "Search and page transactions",
            description = "Filter by type, account, category, date range, amount range and free-text search.")
    public PageResponse<TransactionResponse> list(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurredOn") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        TransactionFilter filter = new TransactionFilter(type, accountId, categoryId, from, to, search,
                minAmount, maxAmount);
        return transactionService.list(userId, filter, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single transaction")
    public TransactionResponse get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return transactionService.get(userId, id);
    }

    @PostMapping
    @Operation(summary = "Create a transaction", description = "Amounts convert to the user's base currency.")
    public ResponseEntity<TransactionResponse> create(@AuthenticationPrincipal UUID userId,
                                                      @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.create(userId, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a transaction")
    public TransactionResponse update(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                                      @Valid @RequestBody TransactionRequest request) {
        return transactionService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a transaction")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        transactionService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Export the filtered transactions as CSV")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount) {
        String csv = transactionService.exportCsv(userId,
                new TransactionFilter(type, accountId, categoryId, from, to, search, minAmount, maxAmount));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("finsight-transactions-" + LocalDate.now() + ".csv").build());
        return new ResponseEntity<>(csv.getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import transactions from CSV",
            description = "Required columns: Date, Amount, Description. Optional: Type, Category, Account, "
                    + "Merchant, Currency. Duplicate rows are skipped.")
    public CsvImportResult importCsv(@AuthenticationPrincipal UUID userId,
                                     @RequestPart("file") MultipartFile file) {
        return transactionService.importCsv(userId, file);
    }
}
