/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.yang.model.parser.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.opendaylight.controller.antlrv4.code.gen.YangLexer;
import org.opendaylight.controller.antlrv4.code.gen.YangParser;
import org.opendaylight.controller.model.api.type.BinaryTypeDefinition;
import org.opendaylight.controller.model.api.type.BitsTypeDefinition;
import org.opendaylight.controller.model.api.type.BitsTypeDefinition.Bit;
import org.opendaylight.controller.model.api.type.DecimalTypeDefinition;
import org.opendaylight.controller.model.api.type.InstanceIdentifierTypeDefinition;
import org.opendaylight.controller.model.api.type.IntegerTypeDefinition;
import org.opendaylight.controller.model.api.type.LengthConstraint;
import org.opendaylight.controller.model.api.type.PatternConstraint;
import org.opendaylight.controller.model.api.type.RangeConstraint;
import org.opendaylight.controller.model.api.type.StringTypeDefinition;
import org.opendaylight.controller.model.util.BaseConstraints;
import org.opendaylight.controller.model.util.BinaryType;
import org.opendaylight.controller.model.util.BitsType;
import org.opendaylight.controller.model.util.StringType;
import org.opendaylight.controller.model.util.UnknownType;
import org.opendaylight.controller.model.util.YangTypesConverter;
import org.opendaylight.controller.yang.common.QName;
import org.opendaylight.controller.yang.model.api.AugmentationSchema;
import org.opendaylight.controller.yang.model.api.DataSchemaNode;
import org.opendaylight.controller.yang.model.api.ExtensionDefinition;
import org.opendaylight.controller.yang.model.api.Module;
import org.opendaylight.controller.yang.model.api.ModuleImport;
import org.opendaylight.controller.yang.model.api.NotificationDefinition;
import org.opendaylight.controller.yang.model.api.RpcDefinition;
import org.opendaylight.controller.yang.model.api.SchemaContext;
import org.opendaylight.controller.yang.model.api.SchemaPath;
import org.opendaylight.controller.yang.model.api.TypeDefinition;
import org.opendaylight.controller.yang.model.parser.api.YangModelParser;
import org.opendaylight.controller.yang.model.parser.builder.api.AugmentationSchemaBuilder;
import org.opendaylight.controller.yang.model.parser.builder.api.AugmentationTargetBuilder;
import org.opendaylight.controller.yang.model.parser.builder.api.ChildNodeBuilder;
import org.opendaylight.controller.yang.model.parser.builder.api.DataSchemaNodeBuilder;
import org.opendaylight.controller.yang.model.parser.builder.api.TypeAwareBuilder;
import org.opendaylight.controller.yang.model.parser.builder.api.TypeDefinitionBuilder;
import org.opendaylight.controller.yang.model.parser.builder.impl.ModuleBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YangModelParserImpl implements YangModelParser {

    private static final Logger logger = LoggerFactory
            .getLogger(YangModelParserImpl.class);

    @Override
    public Module parseYangModel(String yangFile) {
        final Map<String, TreeMap<Date, ModuleBuilder>> modules = loadFiles(yangFile);
        Set<Module> result = build(modules);
        return result.iterator().next();
    }

    @Override
    public Set<Module> parseYangModels(String... yangFiles) {
        final Map<String, TreeMap<Date, ModuleBuilder>> modules = loadFiles(yangFiles);
        Set<Module> result = build(modules);
        return result;
    }

    @Override
    public SchemaContext resolveSchemaContext(Set<Module> modules) {
        return new SchemaContextImpl(modules);
    }

    private Map<String, TreeMap<Date, ModuleBuilder>> loadFiles(
            String... yangFiles) {
        final Map<String, TreeMap<Date, ModuleBuilder>> modules = new HashMap<String, TreeMap<Date, ModuleBuilder>>();

        final YangModelParserListenerImpl yangModelParser = new YangModelParserListenerImpl();
        final ParseTreeWalker walker = new ParseTreeWalker();

        List<ParseTree> trees = parseFiles(yangFiles);

        ModuleBuilder[] builders = new ModuleBuilder[trees.size()];

        for (int i = 0; i < trees.size(); i++) {
            walker.walk(yangModelParser, trees.get(i));
            builders[i] = yangModelParser.getModuleBuilder();
        }

        for (ModuleBuilder builder : builders) {
            final String builderName = builder.getName();
            Date builderRevision = builder.getRevision();
            if (builderRevision == null) {
                builderRevision = createEpochTime();
            }

            TreeMap<Date, ModuleBuilder> builderByRevision = modules
                    .get(builderName);
            if (builderByRevision == null) {
                builderByRevision = new TreeMap<Date, ModuleBuilder>();
            }
            builderByRevision.put(builderRevision, builder);

            modules.put(builderName, builderByRevision);
        }
        return modules;
    }

    private List<ParseTree> parseFiles(String... yangFileNames) {
        List<ParseTree> trees = new ArrayList<ParseTree>();
        for (String fileName : yangFileNames) {
            trees.add(parseFile(fileName));
        }
        return trees;
    }

    private ParseTree parseFile(String yangFileName) {
        ParseTree result = null;
        try {
            final File yangFile = new File(yangFileName);
            final FileInputStream inStream = new FileInputStream(yangFile);
            final ANTLRInputStream input = new ANTLRInputStream(inStream);
            final YangLexer lexer = new YangLexer(input);
            final CommonTokenStream tokens = new CommonTokenStream(lexer);
            final YangParser parser = new YangParser(tokens);
            result = parser.yang();
        } catch (IOException e) {
            logger.warn("Exception while reading yang file: " + yangFileName, e);
        }
        return result;
    }

    private Set<Module> build(Map<String, TreeMap<Date, ModuleBuilder>> modules) {
        // first validate
        for (Map.Entry<String, TreeMap<Date, ModuleBuilder>> entry : modules
                .entrySet()) {
            for (Map.Entry<Date, ModuleBuilder> childEntry : entry.getValue()
                    .entrySet()) {
                ModuleBuilder moduleBuilder = childEntry.getValue();
                validateBuilder(modules, moduleBuilder);
            }
        }
        // then build
        final Set<Module> result = new HashSet<Module>();
        for (Map.Entry<String, TreeMap<Date, ModuleBuilder>> entry : modules
                .entrySet()) {
            final Map<Date, Module> modulesByRevision = new HashMap<Date, Module>();
            for (Map.Entry<Date, ModuleBuilder> childEntry : entry.getValue()
                    .entrySet()) {
                ModuleBuilder moduleBuilder = childEntry.getValue();
                modulesByRevision.put(childEntry.getKey(),
                        moduleBuilder.build());
                result.add(moduleBuilder.build());
            }
        }

        return result;
    }

    private void validateBuilder(
            Map<String, TreeMap<Date, ModuleBuilder>> modules,
            ModuleBuilder builder) {
        resolveTypedefs(modules, builder);
        resolveAugments(modules, builder);
    }

    /**
     * Search for dirty nodes (node which contains UnknownType) and resolve
     * unknown types.
     *
     * @param modules
     *            all available modules
     * @param builder
     *            current module
     */
    private void resolveTypedefs(
            Map<String, TreeMap<Date, ModuleBuilder>> modules,
            ModuleBuilder builder) {
        Map<List<String>, TypeAwareBuilder> dirtyNodes = builder
                .getDirtyNodes();
        if (dirtyNodes.size() == 0) {
            return;
        } else {
            for (Map.Entry<List<String>, TypeAwareBuilder> entry : dirtyNodes
                    .entrySet()) {
                TypeAwareBuilder typeToResolve = entry.getValue();
                Map<TypeDefinitionBuilder, TypeConstraints> foundedTypeDefinitionBuilder = findTypeDefinitionBuilderWithConstraints(
                        modules, entry.getValue(), builder);
                TypeDefinitionBuilder targetType = foundedTypeDefinitionBuilder
                        .entrySet().iterator().next().getKey();
                TypeConstraints constraints = foundedTypeDefinitionBuilder
                        .entrySet().iterator().next().getValue();

                UnknownType ut = (UnknownType) typeToResolve.getType();

                // RANGE
                List<RangeConstraint> ranges = ut.getRangeStatements();
                resolveRanges(ranges, typeToResolve, targetType, modules,
                        builder);

                // LENGTH
                List<LengthConstraint> lengths = ut.getLengthStatements();
                resolveLengths(lengths, typeToResolve, targetType, modules,
                        builder);

                // PATTERN
                List<PatternConstraint> patterns = ut.getPatterns();

                // Fraction Digits
                Integer fractionDigits = ut.getFractionDigits();

                TypeDefinition<?> type = targetType.getBaseType();
                String typeName = type.getQName().getLocalName();

                // MERGE CONSTRAINTS (enumeration and leafref omitted because
                // they have no restrictions)
                if (type instanceof DecimalTypeDefinition) {
                    List<RangeConstraint> fullRanges = new ArrayList<RangeConstraint>();
                    fullRanges.addAll(constraints.getRanges());
                    fullRanges.addAll(ranges);
                    Integer fd = fractionDigits == null ? constraints
                            .getFractionDigits() : fractionDigits;
                    type = YangTypesConverter.javaTypeForBaseYangDecimal64Type(
                            fullRanges, fd);
                } else if (type instanceof IntegerTypeDefinition) {
                    List<RangeConstraint> fullRanges = new ArrayList<RangeConstraint>();
                    fullRanges.addAll(constraints.getRanges());
                    fullRanges.addAll(ranges);
                    if (typeName.startsWith("int")) {
                        type = YangTypesConverter
                                .javaTypeForBaseYangSignedIntegerType(typeName,
                                        fullRanges);
                    } else {
                        type = YangTypesConverter
                                .javaTypeForBaseYangUnsignedIntegerType(
                                        typeName, fullRanges);
                    }
                } else if (type instanceof StringTypeDefinition) {
                    List<LengthConstraint> fullLengths = new ArrayList<LengthConstraint>();
                    fullLengths.addAll(constraints.getLengths());
                    fullLengths.addAll(lengths);
                    List<PatternConstraint> fullPatterns = new ArrayList<PatternConstraint>();
                    fullPatterns.addAll(constraints.getPatterns());
                    fullPatterns.addAll(patterns);
                    type = new StringType(fullLengths, fullPatterns);
                } else if (type instanceof BitsTypeDefinition) {
                    // TODO: add 'length' restriction to BitsType
                    BitsTypeDefinition bitsType = (BitsTypeDefinition) type;
                    List<Bit> bits = bitsType.getBits();
                    type = new BitsType(bits);
                } else if (type instanceof BinaryTypeDefinition) {
                    type = new BinaryType(null, lengths, null);
                } else if (typeName.equals("instance-identifier")) {
                    // TODO: instance-identifier
                    /*
                     * boolean requireInstance = isRequireInstance(typeBody);
                     * type = new InstanceIdentifier(null, requireInstance);
                     */
                }
                typeToResolve.setType(type);
            }
        }
    }

    private TypeDefinitionBuilder findTypeDefinitionBuilder(
            Map<String, TreeMap<Date, ModuleBuilder>> modules,
            TypeAwareBuilder typeBuilder, ModuleBuilder builder) {
        Map<TypeDefinitionBuilder, TypeConstraints> result = findTypeDefinitionBuilderWithConstraints(
                modules, typeBuilder, builder);
        return result.entrySet().iterator().next().getKey();
    }

    private Map<TypeDefinitionBuilder, TypeConstraints> findTypeDefinitionBuilderWithConstraints(
            Map<String, TreeMap<Date, ModuleBuilder>> modules,
            TypeAwareBuilder typeBuilder, ModuleBuilder builder) {
        return findTypeDefinitionBuilderWithConstraints(new TypeConstraints(),
                modules, typeBuilder, builder);
    }

    /**
     * Traverse through all referenced types chain until base YANG type is
     * founded.
     *
     * @param constraints
     *            current type constraints
     * @param modules
     *            all available modules
     * @param typeBuilder
     *            type builder which contains type
     * @param builder
     *            current module
     * @return map, where key is type referenced and value is its constraints
     */
    private Map<TypeDefinitionBuilder, TypeConstraints> findTypeDefinitionBuilderWithConstraints(
            TypeConstraints constraints,
            Map<String, TreeMap<Date, ModuleBuilder>> modules,
            TypeAwareBuilder typeBuilder, ModuleBuilder builder) {
        Map<TypeDefinitionBuilder, TypeConstraints> result = new HashMap<TypeDefinitionBuilder, TypeConstraints>();

        UnknownType type = (UnknownType) typeBuilder.getType();
        QName typeQName = type.getQName();
        String typeName = type.getQName().getLocalName();
        String prefix = typeQName.getPrefix();

        // search for module which contains referenced typedef
        ModuleBuilder dependentModuleBuilder;
        if (prefix.equals(builder.getPrefix())) {
            dependentModuleBuilder = builder;
        } else {
            ModuleImport dependentModuleImport = getModuleImport(builder,
                    prefix);
            String dependentModuleName = dependentModuleImport.getModuleName();
            Date dependentModuleRevision = dependentModuleImport.getRevision();
            TreeMap<Date, ModuleBuilder> moduleBuildersByRevision = modules
                    .get(dependentModuleName);
            if (dependentModuleRevision == null) {
                dependentModuleBuilder = moduleBuildersByRevision.lastEntry()
                        .getValue();
            } else {
                dependentModuleBuilder = moduleBuildersByRevision
                        .get(dependentModuleRevision);
            }
        }

        // pull all typedef statements from dependent module...
        final Set<TypeDefinitionBuilder> typedefs = dependentModuleBuilder
                .getModuleTypedefs();
        // and search for referenced typedef
        TypeDefinitionBuilder lookedUpBuilder = null;
        for (TypeDefinitionBuilder tdb : typedefs) {
            QName qname = tdb.getQName();
            if (qname.getLocalName().equals(typeName)) {
                lookedUpBuilder = tdb;
                break;
            }
        }

        // if referenced type is UnknownType again, search recursively with
        // current constraints
        TypeDefinition<?> referencedType = lookedUpBuilder.getBaseType();
        if (referencedType instanceof UnknownType) {
            UnknownType unknown = (UnknownType) lookedUpBuilder.getBaseType();

            final List<RangeConstraint> ranges = unknown.getRangeStatements();
            constraints.addRanges(ranges);
            final List<LengthConstraint> lengths = unknown
                    .getLengthStatements();
            constraints.addLengths(lengths);
            final List<PatternConstraint> patterns = unknown.getPatterns();
            constraints.addPatterns(patterns);
            return findTypeDefinitionBuilderWithConstraints(constraints,
                    modules, (TypeAwareBuilder) lookedUpBuilder,
                    dependentModuleBuilder);
        } else {
            // pull restriction from this base type and add them to
            // 'constraints'
            if (referencedType instanceof DecimalTypeDefinition) {
                constraints.addRanges(((DecimalTypeDefinition) referencedType)
                        .getRangeStatements());
                constraints
                        .setFractionDigits(((DecimalTypeDefinition) referencedType)
                                .getFractionDigits());
            } else if (referencedType instanceof IntegerTypeDefinition) {
                constraints.addRanges(((IntegerTypeDefinition) referencedType)
                        .getRangeStatements());
            } else if (referencedType instanceof StringTypeDefinition) {
                constraints.addPatterns(((StringTypeDefinition) referencedType)
                        .getPatterns());
            } else if (referencedType instanceof BitsTypeDefinition) {
                // TODO: add 'length' restriction to BitsType
            } else if (referencedType instanceof BinaryTypeDefinition) {
                // TODO
            } else if (referencedType instanceof InstanceIdentifierTypeDefinition) {
                // TODO: instance-identifier
            }

            result.put(lookedUpBuilder, constraints);
            // return lookedUpBuilder;
            return result;
        }
    }

    /**
     * Go through all augmentation definitions and resolve them. This means find
     * referenced node and add child nodes to it.
     *
     * @param modules
     *            all available modules
     * @param builder
     *            current module
     */
    private void resolveAugments(
            Map<String, TreeMap<Date, ModuleBuilder>> modules,
            ModuleBuilder builder) {
        Set<AugmentationSchemaBuilder> augmentBuilders = builder
                .getAddedAugments();

        Set<AugmentationSchema> augments = new HashSet<AugmentationSchema>();
        for (AugmentationSchemaBuilder augmentBuilder : augmentBuilders) {
            SchemaPath augmentTargetSchemaPath = augmentBuilder.getTargetPath();
            String prefix = null;
            List<String> augmentTargetPath = new ArrayList<String>();
            for (QName pathPart : augmentTargetSchemaPath.getPath()) {
                prefix = pathPart.getPrefix();
                augmentTargetPath.add(pathPart.getLocalName());
            }
            ModuleImport dependentModuleImport = getModuleImport(builder,
                    prefix);
            String dependentModuleName = dependentModuleImport.getModuleName();
            augmentTargetPath.add(0, dependentModuleName);

            Date dependentModuleRevision = dependentModuleImport.getRevision();

            TreeMap<Date, ModuleBuilder> moduleBuildersByRevision = modules
                    .get(dependentModuleName);
            ModuleBuilder dependentModule;
            if (dependentModuleRevision == null) {
                dependentModule = moduleBuildersByRevision.lastEntry()
                        .getValue();
            } else {
                dependentModule = moduleBuildersByRevision
                        .get(dependentModuleRevision);
            }

            AugmentationTargetBuilder augmentTarget = (AugmentationTargetBuilder) dependentModule
                    .getNode(augmentTargetPath);
            AugmentationSchema result = augmentBuilder.build();
            augmentTarget.addAugmentation(result);
            fillAugmentTarget(augmentBuilder, (ChildNodeBuilder) augmentTarget);
            augments.add(result);
        }
        builder.setAugmentations(augments);
    }

    /**
     * Add all augment's child nodes to given target.
     *
     * @param augment
     * @param target
     */
    private void fillAugmentTarget(AugmentationSchemaBuilder augment,
            ChildNodeBuilder target) {
        for (DataSchemaNodeBuilder builder : augment.getChildNodes()) {
            builder.setAugmenting(true);
            target.addChildNode(builder);
        }
    }

    /**
     * Get module import referenced by given prefix.
     *
     * @param builder
     *            module to search
     * @param prefix
     *            prefix associated with import
     * @return ModuleImport based on given prefix
     */
    private ModuleImport getModuleImport(ModuleBuilder builder, String prefix) {
        ModuleImport moduleImport = null;
        for (ModuleImport mi : builder.getModuleImports()) {
            if (mi.getPrefix().equals(prefix)) {
                moduleImport = mi;
                break;
            }
        }
        return moduleImport;
    }

    /**
     * Helper method for resolving special 'min' or 'max' values in range
     * constraint
     *
     * @param ranges
     *            ranges to resolve
     * @param typeToResolve
     *            type to resolve
     * @param targetType
     *            target type
     * @param modules
     *            all available modules
     * @param builder
     *            current module
     */
    private void resolveRanges(List<RangeConstraint> ranges,
            TypeAwareBuilder typeToResolve, TypeDefinitionBuilder targetType,
            Map<String, TreeMap<Date, ModuleBuilder>> modules,
            ModuleBuilder builder) {
        if (ranges != null && ranges.size() > 0) {
            Long min = (Long) ranges.get(0).getMin();
            Long max = (Long) ranges.get(ranges.size() - 1).getMax();
            // if range contains one of the special values 'min' or 'max'
            if (min.equals(Long.MIN_VALUE) || max.equals(Long.MAX_VALUE)) {
                Long[] values = parseRangeConstraint(typeToResolve, targetType,
                        modules, builder);
                if (min.equals(Long.MIN_VALUE)) {
                    min = values[0];
                    RangeConstraint oldFirst = ranges.get(0);
                    RangeConstraint newFirst = BaseConstraints.rangeConstraint(
                            min, oldFirst.getMax(), oldFirst.getDescription(),
                            oldFirst.getReference());
                    ranges.set(0, newFirst);
                }
                if (max.equals(Long.MAX_VALUE)) {
                    max = values[1];
                    RangeConstraint oldLast = ranges.get(ranges.size() - 1);
                    RangeConstraint newLast = BaseConstraints.rangeConstraint(
                            oldLast.getMin(), max, oldLast.getDescription(),
                            oldLast.getReference());
                    ranges.set(ranges.size() - 1, newLast);
                }
            }
        }
    }

    /**
     * Helper method for resolving special 'min' or 'max' values in length
     * constraint
     *
     * @param ranges
     *            ranges to resolve
     * @param typeToResolve
     *            type to resolve
     * @param targetType
     *            target type
     * @param modules
     *            all available modules
     * @param builder
     *            current module
     */
    private void resolveLengths(List<LengthConstraint> lengths,
            TypeAwareBuilder typeToResolve, TypeDefinitionBuilder targetType,
            Map<String, TreeMap<Date, ModuleBuilder>> modules,
            ModuleBuilder builder) {
        if (lengths != null && lengths.size() > 0) {
            Long min = lengths.get(0).getMin();
            Long max = lengths.get(lengths.size() - 1).getMax();
            // if length contains one of the special values 'min' or 'max'
            if (min.equals(Long.MIN_VALUE) || max.equals(Long.MAX_VALUE)) {
                Long[] values = parseRangeConstraint(typeToResolve, targetType,
                        modules, builder);
                if (min.equals(Long.MIN_VALUE)) {
                    min = values[0];
                    LengthConstraint oldFirst = lengths.get(0);
                    LengthConstraint newFirst = BaseConstraints
                            .lengthConstraint(min, oldFirst.getMax(),
                                    oldFirst.getDescription(),
                                    oldFirst.getReference());
                    lengths.set(0, newFirst);
                }
                if (max.equals(Long.MAX_VALUE)) {
                    max = values[1];
                    LengthConstraint oldLast = lengths.get(lengths.size() - 1);
                    LengthConstraint newLast = BaseConstraints
                            .lengthConstraint(oldLast.getMin(), max,
                                    oldLast.getDescription(),
                                    oldLast.getReference());
                    lengths.set(lengths.size() - 1, newLast);
                }
            }
        }
    }

    private Long[] parseRangeConstraint(TypeAwareBuilder typeToResolve,
            TypeDefinitionBuilder targetType,
            Map<String, TreeMap<Date, ModuleBuilder>> modules,
            ModuleBuilder builder) {
        TypeDefinition<?> targetBaseType = targetType.getBaseType();

        if (targetBaseType instanceof IntegerTypeDefinition) {
            IntegerTypeDefinition itd = (IntegerTypeDefinition) targetBaseType;
            List<RangeConstraint> ranges = itd.getRangeStatements();
            Long min = (Long) ranges.get(0).getMin();
            Long max = (Long) ranges.get(ranges.size() - 1).getMax();
            return new Long[] { min, max };
        } else if (targetBaseType instanceof DecimalTypeDefinition) {
            DecimalTypeDefinition dtd = (DecimalTypeDefinition) targetBaseType;
            List<RangeConstraint> ranges = dtd.getRangeStatements();
            Long min = (Long) ranges.get(0).getMin();
            Long max = (Long) ranges.get(ranges.size() - 1).getMax();
            return new Long[] { min, max };
        } else {
            return parseRangeConstraint(typeToResolve,
                    findTypeDefinitionBuilder(modules, typeToResolve, builder),
                    modules, builder);
        }
    }

    private Date createEpochTime() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(0);
        return c.getTime();
    }

    private static class SchemaContextImpl implements SchemaContext {
        private final Set<Module> modules;

        private SchemaContextImpl(Set<Module> modules) {
            this.modules = modules;
        }

        @Override
        public Set<DataSchemaNode> getDataDefinitions() {
            final Set<DataSchemaNode> dataDefs = new HashSet<DataSchemaNode>();
            for (Module m : modules) {
                dataDefs.addAll(m.getChildNodes());
            }
            return dataDefs;
        }

        @Override
        public Set<Module> getModules() {
            return modules;
        }

        @Override
        public Set<NotificationDefinition> getNotifications() {
            final Set<NotificationDefinition> notifications = new HashSet<NotificationDefinition>();
            for (Module m : modules) {
                notifications.addAll(m.getNotifications());
            }
            return notifications;
        }

        @Override
        public Set<RpcDefinition> getOperations() {
            final Set<RpcDefinition> rpcs = new HashSet<RpcDefinition>();
            for (Module m : modules) {
                rpcs.addAll(m.getRpcs());
            }
            return rpcs;
        }

        @Override
        public Set<ExtensionDefinition> getExtensions() {
            final Set<ExtensionDefinition> extensions = new HashSet<ExtensionDefinition>();
            for (Module m : modules) {
                extensions.addAll(m.getExtensionSchemaNodes());
            }
            return extensions;
        }
    }

    private static class TypeConstraints {
        private final List<RangeConstraint> ranges = new ArrayList<RangeConstraint>();
        private final List<LengthConstraint> lengths = new ArrayList<LengthConstraint>();
        private final List<PatternConstraint> patterns = new ArrayList<PatternConstraint>();
        private Integer fractionDigits;

        public List<RangeConstraint> getRanges() {
            return ranges;
        }

        public void addRanges(List<RangeConstraint> ranges) {
            this.ranges.addAll(0, ranges);
        }

        public List<LengthConstraint> getLengths() {
            return lengths;
        }

        public void addLengths(List<LengthConstraint> lengths) {
            this.lengths.addAll(0, lengths);
        }

        public List<PatternConstraint> getPatterns() {
            return patterns;
        }

        public void addPatterns(List<PatternConstraint> patterns) {
            this.patterns.addAll(0, patterns);
        }

        public Integer getFractionDigits() {
            return fractionDigits;
        }

        public void setFractionDigits(Integer fractionDigits) {
            if (fractionDigits != null) {
                this.fractionDigits = fractionDigits;
            }
        }
    }

}