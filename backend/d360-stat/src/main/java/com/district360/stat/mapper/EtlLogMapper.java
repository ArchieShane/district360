package com.district360.stat.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

public interface EtlLogMapper {

    class Started {
        public Long id;
        public String source;
    }

    @Insert("INSERT INTO stat.etl_job_log (source, status) VALUES (#{source}, 'running')")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void start(Started job);

    @Update("""
            UPDATE stat.etl_job_log
               SET finished_at = now(), status = #{status}, row_count = #{rowCount}, source_as_of = #{asOf}, message = #{message}
             WHERE id = #{id}
            """)
    void finish(@Param("id") long id, @Param("status") String status, @Param("rowCount") Integer rowCount,
                @Param("asOf") LocalDate asOf, @Param("message") String message);
}
