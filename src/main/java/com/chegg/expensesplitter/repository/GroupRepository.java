package com.chegg.expensesplitter.repository;

import com.chegg.expensesplitter.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, Long> {
}
