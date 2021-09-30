/*
 * Copyright (c) 2008-2021, Massachusetts Institute of Technology (MIT)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.mit.ll.em.api.rs;

import edu.mit.ll.nics.common.entity.Contact;
import edu.mit.ll.nics.common.entity.UserOrg;
import edu.mit.ll.nics.common.entity.User;
import edu.mit.ll.nics.common.entity.UserOrgWorkspace;
import org.hibernate.dialect.SAPDBDialect;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GlobalAdminUserResponse {

    public String message;

    public int count;

    public List<GlobalUser> adminUsers;

    public void setMessage(String message){
        this.message = message;
    }

    public String getMessage(){
        return this.message;
    }

    public int getCount(){
        if(this.adminUsers != null) {
            return this.adminUsers.size();
        }
        return 0;
    }

    public void setAdminUsers(List<GlobalUser> adminUsers){
        this.adminUsers = adminUsers;
    }

    public List<GlobalUser> transform(List<User> users){
        List<GlobalUser> response = new ArrayList<>();
        for(User user : users){
            GlobalUser gUser = new GlobalUser(
                    user.getUsername(),
                    user.getUserId(),
                    user.getFirstname(),
                    user.getLastname()
            );

            Set<UserOrg> userorgs = user.getUserorgs();
            if(userorgs != null) {
                for (UserOrg userOrg : userorgs) {
                    Set<UserOrgWorkspace> workspaces = userOrg.getUserorgworkspaces();
                    if(workspaces != null) {
                        for (UserOrgWorkspace workspace : workspaces) {
                            if (workspace != null) {
                                gUser.addUserOrg(userOrg.getOrg().getName(),
                                        workspace.isEnabled(), userOrg.getSystemrole().getRolename());
                            }
                        }
                    }
                }
            }

            Set<Contact> contacts = user.getContacts();
            List<String> cellPhoneNumbers = new ArrayList<>();
            if(contacts != null){
                for(Contact contact : contacts){
                    if(contact.getContactType().getType().equals("phone_cell")){
                        cellPhoneNumbers.add(contact.getValue());
                    }
                }
                gUser.setCellphone(cellPhoneNumbers);
            }

            response.add(gUser);
        }
        return response;
    }

    public List<GlobalUser> getAdminUsers() {
        return adminUsers;
    }
}