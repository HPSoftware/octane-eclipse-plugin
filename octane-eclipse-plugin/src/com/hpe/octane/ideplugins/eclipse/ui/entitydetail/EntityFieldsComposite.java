/*******************************************************************************
 * © 2017 EntIT Software LLC, a Micro Focus company, L.P.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.hpe.octane.ideplugins.eclipse.ui.entitydetail;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.forms.events.ExpansionEvent;
import org.eclipse.ui.forms.events.IExpansionListener;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;

import com.hpe.adm.nga.sdk.metadata.FieldMetadata;
import com.hpe.adm.nga.sdk.model.ReferenceFieldModel;
import com.hpe.adm.octane.ideplugins.services.MetadataService;
import com.hpe.adm.octane.ideplugins.services.model.EntityModelWrapper;
import com.hpe.octane.ideplugins.eclipse.Activator;
import com.hpe.octane.ideplugins.eclipse.preferences.PluginPreferenceStorage;
import com.hpe.octane.ideplugins.eclipse.preferences.PluginPreferenceStorage.PrefereceChangeHandler;
import com.hpe.octane.ideplugins.eclipse.preferences.PluginPreferenceStorage.PreferenceConstants;
import com.hpe.octane.ideplugins.eclipse.ui.entitydetail.field.DescriptionComposite;
import com.hpe.octane.ideplugins.eclipse.ui.entitydetail.field.FieldEditor;
import com.hpe.octane.ideplugins.eclipse.ui.entitydetail.field.FieldEditorFactory;
import com.hpe.octane.ideplugins.eclipse.ui.util.resource.PlatformResourcesManager;
import com.hpe.octane.ideplugins.eclipse.ui.util.resource.SWTResourceManager;
import com.hpe.octane.ideplugins.eclipse.util.EntityFieldsConstants;

public class EntityFieldsComposite extends Composite {

    // private Color backgroundColor =
    // PlatformResourcesManager.getPlatformBackgroundColor();
    private Color foregroundColor = PlatformResourcesManager.getPlatformForegroundColor();

    private static MetadataService metadataService = Activator.getInstance(MetadataService.class);
    private static FieldEditorFactory fieldEditorFactory = new FieldEditorFactory();

    private Map<String, String> fieldLabelMap;

    private EntityModelWrapper entityModel;

    private Composite fieldsComposite;
    private DescriptionComposite descriptionComposite;

    private FormToolkit formGenerator;

    public EntityFieldsComposite(Composite parent, int style) {
        super(parent, style);
        formGenerator = new FormToolkit(this.getDisplay());
        setLayout(new GridLayout(1, false));

        // Fields
        Section sectionFields = formGenerator.createSection(this, Section.TREE_NODE | Section.EXPANDED);
        sectionFields.setText("Fields");
        sectionFields.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
        fieldsComposite = new Composite(sectionFields, SWT.NONE);
        sectionFields.setClient(fieldsComposite);

        // Description

        // Needed for height hint to work
        Composite descriptionWrapper = new Composite(this, SWT.NONE);
        descriptionWrapper.setLayout(new FillLayout());
        GridData gdSectionDescription = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1);
        gdSectionDescription.heightHint = 500;
        descriptionWrapper.setLayoutData(gdSectionDescription);

        Section sectionDescription = formGenerator.createSection(descriptionWrapper, Section.TREE_NODE | Section.EXPANDED);
        sectionDescription.setText("Description");
        descriptionComposite = new DescriptionComposite(sectionDescription, SWT.NONE);
        sectionDescription.setClient(descriptionComposite);

        formGenerator.createCompositeSeparator(sectionDescription);
        formGenerator.createCompositeSeparator(sectionFields);

        // Expand listeners

        sectionDescription.addExpansionListener(new IExpansionListener() {
            @Override
            public void expansionStateChanging(ExpansionEvent e) {
            }

            @Override
            public void expansionStateChanged(ExpansionEvent e) {
                GridData gdSectionDescription = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1);
                if (sectionDescription.isExpanded()) {
                    gdSectionDescription.heightHint = 500;
                    descriptionWrapper.setLayoutData(gdSectionDescription);
                } else {
                    gdSectionDescription.heightHint = -1;
                    descriptionWrapper.setLayoutData(gdSectionDescription);
                }
                layout(true, true);
            }
        });

        sectionFields.addExpansionListener(new IExpansionListener() {
            @Override
            public void expansionStateChanging(ExpansionEvent expansionEvent) {
            }

            @Override
            public void expansionStateChanged(ExpansionEvent expansionEvent) {
                layout(true, true);
            }
        });

        // Field listener
        PrefereceChangeHandler prefereceChangeHandler = () -> drawEntityFields(entityModel);
        PluginPreferenceStorage.addPrefenceChangeHandler(PreferenceConstants.SHOWN_ENTITY_FIELDS, prefereceChangeHandler);
        addDisposeListener(e -> PluginPreferenceStorage.removePrefenceChangeHandler(PreferenceConstants.SHOWN_ENTITY_FIELDS, prefereceChangeHandler));
    }

    private void drawEntityFields(EntityModelWrapper entityModelWrapper) {
        Set<String> shownFields = PluginPreferenceStorage.getShownEntityFields(entityModelWrapper.getEntityType());
        drawEntityFields(shownFields, entityModelWrapper);
    }

    private void drawEntityFields(Set<String> shownFields, EntityModelWrapper entityModelWrapper) {
        Arrays.stream(fieldsComposite.getChildren()).forEach(child -> child.dispose());

        fieldsComposite.setLayout(new FillLayout(SWT.HORIZONTAL));
        Composite sectionClientLeft = new Composite(fieldsComposite, SWT.NONE);
        sectionClientLeft.setLayout(new GridLayout(2, false));
        sectionClientLeft.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));

        Composite sectionClientRight = new Composite(fieldsComposite, SWT.NONE);
        sectionClientRight.setLayout(new GridLayout(2, false));
        sectionClientRight.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));

        // Skip the description field because it's in another UI component
        // (below other fields)
        Iterator<String> iterator = shownFields.iterator();

        // Number of fields that failed to be displayed
        int skippedFields = 0;

        Collection<FieldMetadata> fieldMetadata = metadataService.getFields(entityModelWrapper.getEntityType());

        for (int i = 0; i < shownFields.size(); i++) {
            String fieldName = iterator.next();

            // Check if the field is valid (exists) before trying to show it
            // If the field name for the given type doesn't return any metadata,
            // we ignore it
            // Default field might be out-dated, and cause detail tab to crash
            boolean fieldMetadataExists = fieldMetadata
                    .stream()
                    .anyMatch(fm -> fm.getName().equals(fieldName));
            
            if(!fieldMetadataExists) {
                ILog log = Activator.getDefault().getLog();
                StringBuilder sbMessage = new StringBuilder();
                sbMessage.append("Faied to create fieldEditor for field ")
                        .append(fieldName)
                        .append(" for type ")
                        .append(entityModelWrapper.getEntityType());
                
                log.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, sbMessage.toString()));

                // Do not show field in detail tab
                skippedFields++;
                continue;
            }

            // Determine if we put the label pair in the left or right container
            Composite columnComposite;
            if (i % 2 == 0) {
                columnComposite = sectionClientLeft;
            } else {
                columnComposite = sectionClientRight;
            }

            // Add the pair of labels for field and value
            CLabel labelFieldName = new CLabel(columnComposite, SWT.NONE);
            labelFieldName.setText(fieldLabelMap.get(fieldName));
            labelFieldName.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT));
            GridData labelFieldNameGridData = new GridData(SWT.LEFT, SWT.CENTER, false, true, 1, 1);
            labelFieldNameGridData.heightHint = 35;
            labelFieldName.setLayoutData(labelFieldNameGridData);

            FieldEditor fieldEditor = fieldEditorFactory.createFieldEditor(columnComposite, entityModelWrapper, fieldName);

            // Attach a clear handler to the sprint field editor
            // TODO: full mvc
            if (EntityFieldsConstants.FIELD_SPRINT.equals(fieldName)) {
                entityModelWrapper.addFieldModelChangedHandler(fieldModel -> {
                    if (EntityFieldsConstants.FIELD_RELEASE.equals(fieldModel.getName())) {
                        entityModelWrapper.setValue(new ReferenceFieldModel(EntityFieldsConstants.FIELD_SPRINT, null));
                        fieldEditor.setField(entityModelWrapper, EntityFieldsConstants.FIELD_SPRINT);
                    }
                });
            }

            GridData fieldEditorGridData = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
            fieldEditorGridData.heightHint = 35;
            Control fieldEditorControl = (Control) fieldEditor;
            fieldEditorControl.setLayoutData(fieldEditorGridData);
            fieldEditorControl.setForeground(foregroundColor);

            // In case of uneven number of fields, the row heights would be
            // inconsistent
            // and Eclipse does not know how to scale correctly
            if (i == shownFields.size() - 1 && (shownFields.size() - skippedFields) % 2 != 0) {
                Composite placeholderComposite = new Composite(sectionClientRight, SWT.NONE);
                GridData placeholderCompositeGridData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
                placeholderCompositeGridData.heightHint = 35;
                placeholderComposite.setLayoutData(placeholderCompositeGridData);
                placeholderComposite.setForeground(foregroundColor);
            }
        }

        // Force redraw
        fieldsComposite.setSize(fieldsComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        layout(true, true);
        redraw();
        update();
    }

    @Override
    public void layout(boolean changed, boolean all) {
        setSize(computeSize(SWT.DEFAULT, SWT.DEFAULT));
        super.layout(changed, all);
    }

    public void setEntityModel(EntityModelWrapper entityModelWrapper) {
        this.entityModel = entityModelWrapper;
        // make a map of the field names and labels
        Collection<FieldMetadata> fieldMetadata = metadataService.getVisibleFields(entityModelWrapper.getEntityType());
        fieldLabelMap = fieldMetadata.stream().collect(Collectors.toMap(FieldMetadata::getName, FieldMetadata::getLabel));
        drawEntityFields(entityModelWrapper);
        descriptionComposite.setEntityModel(entityModelWrapper);
    }

}
