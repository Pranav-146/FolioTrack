package com.foliotrack.service;

import com.foliotrack.dto.PageResponse;
import com.foliotrack.dto.TransactionResponse;
import com.foliotrack.repository.TransactionRepository;
import com.foliotrack.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class TransactionService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(UserRepository userRepository, TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    public PageResponse<TransactionResponse> getTransactions(Long userId, int page, int size) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
