/*
 *   $Id$
 *
 *   Copyright 2010 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.services.export;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ome.model.IObject;
import ome.services.graphs.GraphEntry;
import ome.services.graphs.GraphException;
import ome.services.graphs.GraphSpec;
import ome.services.graphs.GraphState;
import ome.services.graphs.GraphStep;
import ome.services.graphs.GraphStepFactory;
import ome.services.util.Executor;
import ome.system.Principal;
import ome.system.ServiceFactory;

import org.apache.commons.lang.StringUtils;
import org.hibernate.Session;

/**
 * Counter which can be passed into the {@link GraphState} constructor and later
 * used during export. As calls to
 * {@link #create(int, List, GraphSpec, GraphEntry, long[])} are made, the
 * factory keeps links to several object types. After {@link GraphState}
 * initialization, the other methods on the factory can be used to load the
 * objects based on their index.
 *
 * @author Josh Moore, josh at glencoesoftware.com
 * @since Beta4.2.3
 */
public class ExporterStepFactory implements GraphStepFactory {

    private final static String[] TOP_LEVEL = new String[] {
            "BooleanAnnotation", "CommentAnnotation", "Dataset",
            "DoubleAnnotation", "Experiment", "Experimenter", "FileAnnotation",
            "Group", "Image", "Instrument", "ListAnnotation", "LongAnnotation",
            "Plate", "Project", "ROI", "Screen", "TagAnnotation",
            "TermAnnotation", "TimestampAnnotation", "XMLAnnotation" };

    private final Executor ex;

    private final Principal p;

    private final Map<String, Index> data = new HashMap<String, Index>();

    public ExporterStepFactory(Executor ex, Principal p) {
        this.ex = ex;
        this.p = p;
    }

    public GraphStep create(int idx, List<GraphStep> stack, GraphSpec spec,
            GraphEntry entry, long[] ids) throws GraphException {
        ExporterStep step = new ExporterStep(idx, stack, spec, entry, ids);
        update(spec, entry, ids, step);
        return step;
    }

    public int getCount(String name) {
        Index index = data.get(name);
        if (index == null) {
            return -1;
        }
        return index.steps.size();
    }

    public <T extends IObject> T getObject(String name, int...idx)
            throws GraphException {
        return (T) load(name, id(name, idx));
    }

    //
    // Helpers classes and methods
    //

    /**
     * determines the proper object name (i.e. class) for the given entry, and
     * records all the ids in the current tally.
     */
    private void update(GraphSpec spec, GraphEntry entry, long[] ids,
            GraphStep step) throws GraphException {

        if (ids == null) {
            return; // This is a parent-spec
        }

        String[] path = entry.path(spec.getSuperSpec());
        String key = path[path.length - 1];
        Index v = data.get(key);
        if (v == null) {
            int indicesNeeded = depth(path);
            v = new Index(indicesNeeded);
            data.put(key, v);
        }
        v.steps.add(step);
    }

    /**
     * Starts at the back of the provided path, looking for top-level objects.
     * If none is found, then a {@link GraphException} is thrown. Otherwise, the
     * length of the index array which should be passed to
     * {@link #id(String, int[])} is returned.
     *
     * @param path
     * @return
     * @throws GraphException
     */
    private int depth(String[] path) throws GraphException {
        for (int i = path.length - 1; i >= 0; i--) {
            String part = path[i];
            if (0 <= Arrays.binarySearch(TOP_LEVEL, part)) {
                return path.length - i + 1; // length of search indexes needed
            }
        }
        throw new GraphException("Path without top-level:"
                + StringUtils.join(path));
    }

    /**
     * Lookup the object id for the object with the given name at the given
     * indexes. If the length of the indexes does not match those stored
     */
    private long id(String name, int...idx) throws GraphException {
        Index v = data.get(name);
        if (v == null) {
            throw new GraphException("No indexes for " + name
                    + ". Use getCount first!");
        }

        if (v.indicesNeeded != idx.length) {
            throw new GraphException("Wrong index sizes! ExpectedL" +
                    v.indicesNeeded + ". Got: " + idx.length);

        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private IObject load(String name, long id) {
        return (IObject) ex.execute(p, new Load(this, name, id));
    }

    /**
     * {@link Executor.SimpleWork} implementation which loads an object based on
     * its class and id. The returned object will be disconnected from any
     * session and can be used just as a single DB row.
     */
    private static class Load extends Executor.SimpleWork {

        private final String name;

        private final Long id;

        public Load(ExporterStepFactory factory, String name, Long id) {
            super(factory, "load", name, id);
            this.name = name;
            this.id = id;
        }

        public Object doWork(Session session, ServiceFactory sf) {
            return session.get(name, id);
        }

    }

    /**
     *
     */
    private static class Index {

        final private int indicesNeeded;

        final private List<GraphStep> steps = new ArrayList<GraphStep>();

        Index(int indicesNeeded) {
            this.indicesNeeded = indicesNeeded;
        }
    }

}