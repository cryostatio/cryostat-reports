package io.cryostat.reports;

import java.util.Set;
import java.util.concurrent.Executors;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Produces;

import io.cryostat.core.log.Logger;
import io.cryostat.core.reports.InterruptibleReportGenerator;
import io.cryostat.core.sys.FileSystem;

public class Producers {

    @Produces
    @ApplicationScoped
    InterruptibleReportGenerator produceReportGenerator() {
        return new InterruptibleReportGenerator(
                Logger.INSTANCE, Set.of(), Executors.newWorkStealingPool());
    }

    @Produces
    @ApplicationScoped
    FileSystem produceFileSystem() {
        return new FileSystem();
    }
}
