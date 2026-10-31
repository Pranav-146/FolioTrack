package com.foliotrack.service;

import com.foliotrack.dto.PageResponse;
import com.foliotrack.dto.TransactionResponse;
import com.foliotrack.exception.NotFoundException;
import com.foliotrack.repository.TransactionRepository;
import com.foliotrack.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(UserRepository userRepository, TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getTransactions(Long userId, int page, int size) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User not found: " + userId);
        }
        return PageResponse.from(
                transactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(page, size)),
                TransactionResponse::from);
    }
}
