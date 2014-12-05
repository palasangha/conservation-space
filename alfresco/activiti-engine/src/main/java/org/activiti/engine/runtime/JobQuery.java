/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.engine.runtime;

import java.util.Date;

import org.activiti.engine.query.Query;


// TODO: Auto-generated Javadoc
/**
 * Allows programmatic querying of {@link Job}s.
 * 
 * @author Joram Barrez
 * @author Falko Menge
 */
public interface JobQuery extends Query<JobQuery, Job> {
  
  /**
   * Only select jobs with the given id.
   *
   * @param jobId the job id
   * @return the job query
   */
  JobQuery jobId(String jobId);
  
  /**
   * Only select jobs which exist for the given process instance. *
   *
   * @param processInstanceId the process instance id
   * @return the job query
   */
  JobQuery processInstanceId(String processInstanceId);
  
  /**
   * Only select jobs which exist for the given execution.
   *
   * @param executionId the execution id
   * @return the job query
   */ 
  JobQuery executionId(String executionId);

  /**
   * Only select jobs which have retries left.
   *
   * @return the job query
   */
  JobQuery withRetriesLeft();

  /**
   * Only select jobs which are executable,
   * ie. retries &gt; 0 and duedate is null or duedate is in the past *
   *
   * @return the job query
   */
  JobQuery executable();

  /**
   * Only select jobs that are timers.
   * Cannot be used together with {@link #messages()}
   *
   * @return the job query
   */
  JobQuery timers();
 
  /**
   * Only select jobs that are messages.
   * Cannot be used together with {@link #timers()}
   *
   * @return the job query
   */
  JobQuery messages();
  
  /**
   * Only select jobs where the duedate is lower then the given date.
   *
   * @param date the date
   * @return the job query
   */
  JobQuery duedateLowerThen(Date date);
  
  /**
   * Only select jobs where the duedate is lower then or equals the given date.
   *
   * @param date the date
   * @return the job query
   */
  JobQuery duedateLowerThenOrEquals(Date date);
  
  /**
   * Only select jobs where the duedate is higher then the given date.
   *
   * @param date the date
   * @return the job query
   */
  JobQuery duedateHigherThen(Date date);
  
  /**
   * Only select jobs where the duedate is higher then or equals the given date.
   *
   * @param date the date
   * @return the job query
   */
  JobQuery duedateHigherThenOrEquals(Date date);
  
  /**
   * Only select jobs that failed due to an exception.
   *
   * @return the job query
   */
  JobQuery withException();

  /**
   * Only select jobs that failed due to an exception with the given message.
   *
   * @param exceptionMessage the exception message
   * @return the job query
   */
  JobQuery exceptionMessage(String exceptionMessage);

  //sorting //////////////////////////////////////////
  
  /**
   * Order by job id (needs to be followed by {@link #asc()} or {@link #desc()}).
   *
   * @return the job query
   */
  JobQuery orderByJobId();
  
  /**
   * Order by duedate (needs to be followed by {@link #asc()} or {@link #desc()}).
   *
   * @return the job query
   */
  JobQuery orderByJobDuedate();
  
  /**
   * Order by retries (needs to be followed by {@link #asc()} or {@link #desc()}).
   *
   * @return the job query
   */
  JobQuery orderByJobRetries();

  /**
   * Order by process instance id (needs to be followed by {@link #asc()} or {@link #desc()}).
   *
   * @return the job query
   */
  JobQuery orderByProcessInstanceId();
  
  /**
   * Order by execution id (needs to be followed by {@link #asc()} or {@link #desc()}).
   *
   * @return the job query
   */
  JobQuery orderByExecutionId();
}