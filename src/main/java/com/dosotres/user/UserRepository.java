package com.dosotres.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /** Filtra, de la lista de candidatos, quiénes quieren avisos de pedido creado. */
    List<Long> findIdByIdInAndNotifyOnRequestCreatedTrue(List<Long> ids);

    /** Filtra, de la lista de candidatos, quiénes quieren avisos de "alguien oró". */
    List<Long> findIdByIdInAndNotifyOnPrayedTrue(List<Long> ids);

    /** Filtra, de la lista de candidatos, quiénes quieren avisos de pedido respondido. */
    List<Long> findIdByIdInAndNotifyOnAnsweredTrue(List<Long> ids);
}
