package org.mifosplatform.infrastructure.jobs.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduledJobDetailRepository extends JpaRepository<ScheduledJobDetail, Long>,
        JpaSpecificationExecutor<ScheduledJobDetail> {

    @Query("from ScheduledJobDetail jobDetail where jobDetail.jobKey = :jobKey")
    ScheduledJobDetail findByJobKey(@Param("jobKey") String jobKey);
    
    @Query("from ScheduledJobDetail jobDetail where jobDetail.id=:jobId")
    ScheduledJobDetail findByJobId(@Param("jobId") Long jobId);

}
