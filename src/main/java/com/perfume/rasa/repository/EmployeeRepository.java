package com.perfume.rasa.repository;

import com.perfume.rasa.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Employee> {
    Optional<Employee> findByEmployeeId(String employeeId);
    Optional<Employee> findByUserId(Long userId);
    Optional<Employee> findByUserEmail(String email);
    boolean existsByEmployeeId(String employeeId);
}
