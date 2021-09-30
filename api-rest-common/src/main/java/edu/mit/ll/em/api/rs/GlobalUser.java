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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GlobalUser extends APIBean{

    private String username;
    private int userid;
    private String firstname;
    private String lastname;
    private List<String> cellphone;
    private List<UserOrg> userorgs = new ArrayList();

    public GlobalUser(
            String username,
            int userid,
            String firstname,
            String lastname
    ) {
        super();

        this.username = username;
        this.userid = userid;
        this.firstname = firstname;
        this.lastname = lastname;
    }

    public String getUsername(){
        return this.username;
    }

    public int getUserid(){
        return this.userid;
    }

    public String getFirstname(){
        return this.firstname;
    }

    public String getLastname(){
        return this.lastname;
    }

    public List<String> getCellphone() {
        return cellphone;
    }

    public void setCellphone(List<String> cellphone) {
        this.cellphone = cellphone;
    }

    public List<UserOrg> getUserorgs(){
        return this.userorgs;
    }

    public void addUserOrg(String name, boolean enabled, String role){
        this.userorgs.add(new UserOrg(name, enabled, role));
    }

    private class UserOrg {
        private boolean enabled;
        private String name;
        private String role;

        public UserOrg(String name, boolean enabled, String role) {
            this.name = name;
            this.enabled = enabled;
            this.role = role;
        }

        public boolean getEnabled(){
            return this.enabled;
        }

        public String getName(){
            return this.name;
        }

        public String getRole(){
            return this.role;
        }
    }
}
