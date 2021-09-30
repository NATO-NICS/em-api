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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.mit.ll.nics.common.entity.IncidentType;
import edu.mit.ll.nics.common.entity.OrgIncidentType;

public class OrgIncidentTypeServiceResponse {

	private String message;
	
	private int orgId;
	
	private List<OrgIncidentType> activeIncidentTypes = new ArrayList<>();
	
	private List<IncidentType> inactiveIncidentTypes = new ArrayList<>();
	
	private List<IncidentType> defaultIncidentTypes = new ArrayList<>();

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
	
	public List<IncidentType> getDefaultIncidentTypes() {
		return defaultIncidentTypes;
	}

	public void setDefaultIncidentTypes(List<IncidentType> defaultIncidentTypes) {
		this.defaultIncidentTypes = defaultIncidentTypes;
	}
	
	public List<OrgIncidentType> getActiveIncidentTypes() {
		return activeIncidentTypes;
	}

	public void setActiveIncidentTypes(List<OrgIncidentType> activeIncidentTypes) {
		this.activeIncidentTypes = activeIncidentTypes;
	}
	
	public List<IncidentType> getInactiveIncidentTypes() {
		return inactiveIncidentTypes;
	}

	public void setInactiveIncidentTypes(List<IncidentType> inactiveIncidentTypes) {
		this.inactiveIncidentTypes = inactiveIncidentTypes;
	}
	
	public int getOrgId(){
		return this.orgId;
	}
	
	public void setOrgId(int orgId){
		this.orgId = orgId;
	}
	
	public String toString() {
		return "OrgIncidentTypeServiceResponse [orgId=" + orgId + ", message="
				+ message + ", activeIncidentTypes=" +
				( (activeIncidentTypes == null) ? "null" : Arrays.toString(activeIncidentTypes.toArray() )) +
				", inactiveIncidentTypes=" +
				( (inactiveIncidentTypes == null) ? "null" : Arrays.toString(inactiveIncidentTypes.toArray() )) +
				", defaultIncidentTypes=" +
				( (defaultIncidentTypes == null) ? "null" : Arrays.toString(defaultIncidentTypes.toArray() )) + "]";
	}
}

