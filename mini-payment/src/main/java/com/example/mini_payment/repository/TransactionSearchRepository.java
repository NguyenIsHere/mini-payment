package com.example.mini_payment.repository;

import com.example.mini_payment.document.TransactionDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionSearchRepository extends ElasticsearchRepository<TransactionDocument, String> {
    // Có thể viết thêm custom query search tại đây nếu cần sau này
}