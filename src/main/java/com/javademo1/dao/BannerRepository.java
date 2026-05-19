package com.javademo1.dao;

import com.javademo1.pojo.Banner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    List<Banner> findAllByOrderBySortNumAscCreateTimeDesc();

    List<Banner> findByStatusOrderBySortNumAscCreateTimeDesc(String status);
}

