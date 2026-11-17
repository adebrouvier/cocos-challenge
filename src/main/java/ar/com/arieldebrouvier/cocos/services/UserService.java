package ar.com.arieldebrouvier.cocos.services;

import ar.com.arieldebrouvier.cocos.exceptions.NotFoundException;
import ar.com.arieldebrouvier.cocos.models.User;
import ar.com.arieldebrouvier.cocos.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Access to users (accounts). */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Finds the user that owns an account.
     *
     * @param accountNumber the account number
     * @return the user
     * @throws NotFoundException if no user has that account number
     */
    public User getByAccountNumber(String accountNumber) {
        return userRepository.findByAccountNumber(accountNumber).orElseThrow(() -> notFound(accountNumber));
    }

    /**
     * Same as {@link #getByAccountNumber}, but also locks the user's row ({@code SELECT ... FOR UPDATE}) until
     * the caller's transaction ends. Used to serialize operations that validate a user's balances.
     * Must be called inside an existing transaction, otherwise the lock would be released immediately.
     *
     * @param accountNumber the account number
     * @return the user, whose row stays locked until the caller's transaction commits or rolls back
     * @throws NotFoundException if no user has that account number
     * @throws org.springframework.transaction.IllegalTransactionStateException if called outside a transaction
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public User getByAccountNumberForUpdate(String accountNumber) {
        return userRepository.findByAccountNumberForUpdate(accountNumber).orElseThrow(() -> notFound(accountNumber));
    }

    private static NotFoundException notFound(String accountNumber) {
        return new NotFoundException("Account " + accountNumber + " not found");
    }
}
