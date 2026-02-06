package com.cosmate.repository;

import com.cosmate.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Integer> {
    // JpaRepository đã lo hết các hàm save, findAll, findById cho ông rồi.
}