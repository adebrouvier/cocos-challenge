package ar.com.arieldebrouvier.cocos.repositories;

import ar.com.arieldebrouvier.cocos.models.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByAccountNumber(String accountNumber);

    /**
     * Loads the user with {@code SELECT ... FOR UPDATE}. Order submission takes this lock so that
     * concurrent orders of the same user are validated against balances one at a time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.accountNumber = :accountNumber")
    Optional<User> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
}
