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

package org.activiti.engine.impl.cmd;

import java.io.Serializable;

import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.AttachmentEntity;
import org.activiti.engine.task.Attachment;


// TODO: Auto-generated Javadoc
/**
 * The Class SaveAttachmentCmd.
 *
 * @author Tom Baeyens
 */
public class SaveAttachmentCmd implements Command<Object>, Serializable {

  /** The Constant serialVersionUID. */
  private static final long serialVersionUID = 1L;
  
  /** The attachment. */
  protected Attachment attachment;
  
  /**
   * Instantiates a new save attachment cmd.
   *
   * @param attachment the attachment
   */
  public SaveAttachmentCmd(Attachment attachment) {
    this.attachment = attachment;
  }

  /* (non-Javadoc)
   * @see org.activiti.engine.impl.interceptor.Command#execute(org.activiti.engine.impl.interceptor.CommandContext)
   */
  public Object execute(CommandContext commandContext) {
    AttachmentEntity updateAttachment = commandContext
      .getDbSqlSession()
      .selectById(AttachmentEntity.class, attachment.getId());
    
    updateAttachment.setName(attachment.getName());
    updateAttachment.setDescription(attachment.getDescription());
    
    return null;
  }
}