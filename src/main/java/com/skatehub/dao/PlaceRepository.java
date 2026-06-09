package com.skatehub.dao;

import com.skatehub.pojo.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place> findAllByOrderByScoreDescCreateTimeDesc();
}
