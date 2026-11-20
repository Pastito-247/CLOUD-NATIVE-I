package com.ecommerce.report.repository;

import com.ecommerce.report.model.ReportKpi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportKpiRepository extends JpaRepository<ReportKpi, Long> {
}
