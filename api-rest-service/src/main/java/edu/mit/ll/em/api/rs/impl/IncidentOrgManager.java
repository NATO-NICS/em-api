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
package edu.mit.ll.em.api.rs.impl;

import edu.mit.ll.nics.common.entity.IncidentOrg;
import edu.mit.ll.nics.nicsdao.impl.IncidentDAOImpl;
import org.springframework.dao.DataAccessException;

import java.util.*;

import edu.mit.ll.nics.nicsdao.impl.OrgDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.IncidentDAOImpl;

public class IncidentOrgManager {

    private OrgDAOImpl orgDao;
    private IncidentDAOImpl incidentDao;

    public IncidentOrgManager(OrgDAOImpl orgDaoImpl, IncidentDAOImpl incidentDaoImpl) {
        orgDao = orgDaoImpl;
        incidentDao = incidentDaoImpl;
    }

    /**
     * Finds the owning org of the specified incident, and determines whether or not the requested action would result
     * in the owning org being locked out of the specified Incident
     *
     * @param before     the current list of orgIds locked to this incident
     * @param requested  the incoming requested orgIds to be added/removed bsed on the which parameter
     * @param incidentId the Id of the incident to check incident org mappings and org ownership on
     * @return true if the owning org will be locked out by this action, false otherwise
     *
     * @throws Exception when the owning org can't be determined, or other unhandled exception occurs
     */
    public boolean isOwningOrgLockedOut(List<Integer> before, List<IncidentOrg> requested, Integer incidentId)
            throws Exception {

        List<Integer> requestedOrgs = this.getOrgIds(requested);

        int owningOrgId;
        try {
            owningOrgId = incidentDao.getOwningOrgId(incidentId);

        } catch(DataAccessException e) {
            throw new Exception("Error retrieving the owning orgId of IncidentID " +
                    incidentId + ": " + e.getMessage());
        }

        return (requestedOrgs.contains(owningOrgId));
    }

    public List<IncidentOrg> getParentOrgs(List<IncidentOrg> incidentOrgs,
                                           int incidentId, int userId) {

        List<IncidentOrg> parentOrgs = new ArrayList();

        if(incidentId != -1) {
            //Make sure the owning org is in the list
            int owningOrgId = incidentDao.getOwningOrgId(incidentId);

            if(!incidentOrgs.contains(owningOrgId)) {
                parentOrgs.add(new IncidentOrg(
                        owningOrgId, incidentId, userId
                ));
            }
        }

        List<Integer> orgIds = new ArrayList();
        for(IncidentOrg inc : incidentOrgs) {
            orgIds.add(inc.getOrgid());
        }

        List<Integer> parentIds = orgDao.getAllParents(orgIds);
        if(parentIds != null && parentIds.size() > 0) {
            for(int parentId : parentIds) {
                parentOrgs.add(new IncidentOrg(
                        parentId, incidentId, userId
                ));
            }
        }

        return parentOrgs;
    }

    public boolean validateRemoval(IncidentOrg incOrg,
                                   List<Integer> incidentOrgs,
                                   List<Integer> orgsToBeRemoved) {
        List<Integer> childIds =
                orgDao.getAllChildren(Arrays.asList(incOrg.getOrgid()));

        for(int childId : childIds) {
            if(incidentOrgs.contains(childId) &&
                    !orgsToBeRemoved.contains(childId)) {
                return false;
            }
        }

        return true;
    }

    public List<Integer> getOrgIds(List<IncidentOrg> incidentOrgs) {
        List<Integer> ids = new ArrayList();
        for(IncidentOrg incOrg : incidentOrgs) {
            ids.add(incOrg.getOrgid());
        }

        return ids;
    }

    public Set<IncidentOrg> convertToSet(List<IncidentOrg> incidentOrgs) {
        HashSet<IncidentOrg> set = new HashSet<IncidentOrg>();
        for(IncidentOrg io : incidentOrgs) {
            set.add(io);
        }
        return set;
    }

    public List<IncidentOrg> convertToList(Set<IncidentOrg> incidentOrgs) {
        List<IncidentOrg> list = new ArrayList<IncidentOrg>();
        for(IncidentOrg io : incidentOrgs) {
            list.add(io);
        }
        return list;
    }

}
