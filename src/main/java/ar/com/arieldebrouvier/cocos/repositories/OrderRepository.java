package ar.com.arieldebrouvier.cocos.repositories;

import ar.com.arieldebrouvier.cocos.models.Order;
import ar.com.arieldebrouvier.cocos.models.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    List<Order> findByUserIdAndStatusInOrderByDatetimeAscIdAsc(Integer userId, Collection<OrderStatus> statuses);
}
