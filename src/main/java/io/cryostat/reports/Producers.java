/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.reports;

import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

import io.cryostat.core.reports.InterruptibleReportGenerator;
import io.cryostat.core.sys.FileSystem;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Produces;

public class Producers {

    @Produces
    // RequestScoped so that each individual report generation request has its own interruptible
    // generator with an independent task queueing thread which dispatches to the shared common pool
    @RequestScoped
    InterruptibleReportGenerator produceReportGenerator() {
        boolean singleThread =
                Runtime.getRuntime().availableProcessors() < 2
                        || Boolean.getBoolean(ReportResource.SINGLETHREAD_PROPERTY);
        return new InterruptibleReportGenerator(
                singleThread ? Executors.newSingleThreadExecutor() : ForkJoinPool.commonPool());
    }

    @Produces
    @ApplicationScoped
    FileSystem produceFileSystem() {
        return new FileSystem();
    }
}
