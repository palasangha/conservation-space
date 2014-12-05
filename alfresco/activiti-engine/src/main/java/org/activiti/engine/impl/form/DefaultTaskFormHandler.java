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

package org.activiti.engine.impl.form;

import org.activiti.engine.form.TaskFormData;
import org.activiti.engine.impl.persistence.entity.TaskEntity;



// TODO: Auto-generated Javadoc
/**
 * The Class DefaultTaskFormHandler.
 *
 * @author Tom Baeyens
 */
public class DefaultTaskFormHandler extends DefaultFormHandler implements TaskFormHandler {

  /* (non-Javadoc)
   * @see org.activiti.engine.impl.form.TaskFormHandler#createTaskForm(org.activiti.engine.impl.persistence.entity.TaskEntity)
   */
  public TaskFormData createTaskForm(TaskEntity task) {
    TaskFormDataImpl taskFormData = new TaskFormDataImpl();
    taskFormData.setFormKey(formKey);
    taskFormData.setDeploymentId(deploymentId);
    taskFormData.setTask(task);
    initializeFormProperties(taskFormData, task.getExecution());
    return taskFormData;
  }
}