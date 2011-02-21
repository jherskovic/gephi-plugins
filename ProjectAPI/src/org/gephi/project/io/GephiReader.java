/*
Copyright 2008-2010 Gephi
Authors : Mathieu Bastian <mathieu.bastian@gephi.org>
Website : http://www.gephi.org

This file is part of Gephi.

Gephi is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

Gephi is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with Gephi.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gephi.project.io;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import org.gephi.project.impl.ProjectImpl;
import org.gephi.project.impl.ProjectInformationImpl;
import org.gephi.project.impl.WorkspaceProviderImpl;
import org.gephi.project.api.Project;
import org.gephi.project.api.Workspace;
import org.gephi.workspace.impl.WorkspaceImpl;
import org.gephi.workspace.impl.WorkspaceInformationImpl;
import org.gephi.project.spi.WorkspacePersistenceProvider;
import org.openide.util.Cancellable;
import org.openide.util.Lookup;

/**
 *
 * @author Mathieu Bastian
 */
public class GephiReader implements Cancellable {

    private ProjectImpl project;
    private boolean cancel = false;
    private Map<String, WorkspacePersistenceProvider> providers;
    private WorkspacePersistenceProvider currentProvider;

    public GephiReader() {
        providers = new LinkedHashMap<String, WorkspacePersistenceProvider>();
        for (WorkspacePersistenceProvider w : Lookup.getDefault().lookupAll(WorkspacePersistenceProvider.class)) {
            try {
                String id = w.getIdentifier();
                if (id != null && !id.isEmpty()) {
                    providers.put(w.getIdentifier(), w);
                }
            } catch (Exception e) {
            }
        }
    }

    public boolean cancel() {
        cancel = true;
        return true;
    }

    public Project readAll(XMLStreamReader reader, Project project) throws Exception {
        ProjectInformationImpl info = project.getLookup().lookup(ProjectInformationImpl.class);
        WorkspaceProviderImpl workspaces = project.getLookup().lookup(WorkspaceProviderImpl.class);
        this.project = (ProjectImpl) project;

        boolean end = false;
        while (reader.hasNext() && !end) {
            Integer eventType = reader.next();
            if (eventType.equals(XMLEvent.START_ELEMENT)) {
                String name = reader.getLocalName();
                if ("gephiFile".equalsIgnoreCase(name)) {
                    //Version
                    String version = reader.getAttributeValue(null, "version");
                    if (version == null || version.isEmpty() || Double.parseDouble(version) < 0.7) {
                        throw new GephiFormatException("Gephi project file version must be at least 0.7");
                    }
                } else if ("project".equalsIgnoreCase(name)) {
                    info.setName(reader.getAttributeValue(null, "name"));
                } else if ("workspace".equalsIgnoreCase(name)) {
                    Workspace workspace = readWorkspace(reader);

                    //Current workspace
                    if (workspace.getLookup().lookup(WorkspaceInformationImpl.class).isOpen()) {
                        workspaces.setCurrentWorkspace(workspace);
                    }
                }
            } else if (eventType.equals(XMLStreamReader.END_ELEMENT)) {
                if ("project".equalsIgnoreCase(reader.getLocalName())) {
                    end = true;
                }
            }
        }

        return project;
    }

    public Workspace readWorkspace(XMLStreamReader reader) throws Exception {
        WorkspaceImpl workspace = project.getLookup().lookup(WorkspaceProviderImpl.class).newWorkspace();
        WorkspaceInformationImpl info = workspace.getLookup().lookup(WorkspaceInformationImpl.class);

        //Name
        info.setName(reader.getAttributeValue(null, "name"));

        //Status
        String workspaceStatus = reader.getAttributeValue(null, "status");
        if (workspaceStatus.equals("open")) {
            info.open();
        } else if (workspaceStatus.equals("closed")) {
            info.close();
        } else {
            info.invalid();
        }

        //WorkspacePersistent
        readWorkspaceChildren(workspace, reader);
        if (currentProvider != null) {
            //One provider not correctly closed
            throw new GephiFormatException("The '" + currentProvider.getIdentifier() + "' persistence provider is not ending read.");
        }

        return workspace;
    }

    public void readWorkspaceChildren(Workspace workspace, XMLStreamReader reader) throws Exception {
        boolean end = false;
        while (reader.hasNext() && !end) {
            Integer eventType = reader.next();
            if (eventType.equals(XMLEvent.START_ELEMENT)) {
                String name = reader.getLocalName();
                WorkspacePersistenceProvider pp = providers.get(name);
                if (pp != null) {
                    currentProvider = pp;
                    try {
                        pp.readXML(reader, workspace);
                    } catch (UnsupportedOperationException e) {
                    }
                }
            } else if (eventType.equals(XMLStreamReader.END_ELEMENT)) {
                if ("workspace".equalsIgnoreCase(reader.getLocalName())) {
                    end = true;
                    currentProvider = null;
                }
            }
        }
    }
}
