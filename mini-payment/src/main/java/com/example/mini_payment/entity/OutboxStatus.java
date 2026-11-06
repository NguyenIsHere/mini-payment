package com.example.mini_payment.entity;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    DONE,
    FAILED_NEEDS_MANUAL_FIX
}