package com.fxz.rpc.feign.plus.core.reporter;

import com.alibaba.fastjson.JSON;
import com.fxz.fuled.dynamic.threadpool.pojo.ReporterDto;
import com.fxz.fuled.dynamic.threadpool.reporter.Reporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
@Slf4j
public class LogReporter implements Reporter {
    @Override
    public void report(List<ReporterDto> records) {
        if (!CollectionUtils.isEmpty(records)) {
            records.stream().forEach(r -> {
                log.info("Reporter->{}", JSON.toJSONString(r));
            });
        }
    }
}
