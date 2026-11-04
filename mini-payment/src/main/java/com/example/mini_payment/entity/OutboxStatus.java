package com.example.mini_payment.entity;

public enum OutboxStatus {
    PENDING,
    DONE,
    FAILED_NEEDS_MANUAL_FIX
}