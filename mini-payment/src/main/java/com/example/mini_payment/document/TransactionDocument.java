package com.example.mini_payment.document;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(indexName = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String transactionCode;

    @Field(type = FieldType.Keyword)
    private String sourceAccountNumber;

    @Field(type = FieldType.Keyword)
    private String targetAccountNumber;

    @Field(type = FieldType.Double)
    private BigDecimal amount;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;
}