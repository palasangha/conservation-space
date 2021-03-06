/*
 *
 */
package com.sirma.itt.cmf.integration.service;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.repo.search.QueryParameterDefImpl;
import org.alfresco.repo.security.SEIPTenantIntegration;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.security.permissions.AccessDeniedException;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.QueryParameterDefinition;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.ISO9075;
import org.alfresco.util.ModelUtil;
import org.alfresco.util.Pair;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.util.ReflectionUtils;

import com.ibm.icu.util.Calendar;
import com.sirma.itt.cmf.integration.ServiceProxy;
import com.sirma.itt.cmf.integration.exception.SEIPRuntimeException;
import com.sirma.itt.cmf.integration.model.CMFModel;

/**
 * The class CMFService has basic methods for search and obtain specific
 * information for cmf model.
 * 
 * @author borislav banchev
 */
public class CMFService {

	/** the logger. */
	private static final Logger LOGGER = Logger.getLogger(CMFService.class);

	/** The Constant DEBUG_ENABLED. */
	private static final boolean DEBUG_ENABLED = LOGGER.isDebugEnabled();

	/** The Constant TRACE_ENABLED. */
	private static final boolean TRACE_ENABLED = LOGGER.isTraceEnabled();

	private static final String KEY_PAGING_TOTAL = "total";

	/** semaphore lock. */
	private final ReentrantReadWriteLock spaceCreationLock = new ReentrantReadWriteLock(true);

	/** The Constant DOCUMENT_TYPES. */
	private static final Set<String> DOCUMENT_ASPECTS = new TreeSet<String>();
	// master list template searches
	/** The Constant QUERY_TEMPLATES. */
	private static final ArrayList<Map<String, String>> QUERY_TEMPLATES = new ArrayList<Map<String, String>>();

	/** The Constant KEY_KEYWORDS. */
	private static final String KEY_KEYWORDS = "keywords";
	/** The search service. */
	private SearchService searchService;
	/** the node service. */
	private NodeService nodeService;
	/** the lock service. */
	private CMFLockService cmfLockService;
	/** the service registry. */
	protected ServiceRegistry serviceRegistry;
	/** The repository. */
	private Repository repository;
	/** service proxy. */
	private ServiceProxy serviceProxy;
	/**
	 * The proxy of
	 * {@link org.alfresco.repo.security.sync.TenantChainingUserRegistrySynchronizer}
	 * .
	 */
	private Object chainingUserRegistrySynchronizer;

	/** The namespace service. */
	private NamespaceService namespaceService;

	static {
		Map<String, String> templateEntry = new HashMap<String, String>(2);
		templateEntry.put("field", KEY_KEYWORDS);
		templateEntry.put("template", "%(cm:name cm:title cm:description TEXT)");
		QUERY_TEMPLATES.add(templateEntry);
		DOCUMENT_ASPECTS.add(CMFModel.ASPECT_CMF_CASE_ATTACHED_DOC.toString());
		DOCUMENT_ASPECTS.add(CMFModel.ASPECT_CMF_CASE_STRUCTURED_DOC.toString());
		DOCUMENT_ASPECTS.add(CMFModel.ASPECT_CMF_CASE_COMMON_DOC.toString());

	}

	/**
	 * Gets the node by path. If path ends with / it is removed
	 * 
	 * @param path
	 *            the path to search for
	 * @return the node by path or null if !=1 results found
	 */
	public NodeRef getNodeByPath(String path) {
		// optimized search
		SearchParameters searchParameters = new SearchParameters();
		searchParameters.setLanguage(getServiceProxy().getDefaultQueryLanguage());
		// remove last /
		String pathLocal = path;
		if (pathLocal.endsWith("/")) {
			pathLocal = pathLocal.substring(0, pathLocal.length() - 1);
		}
		String query = "PATH:\"" + pathLocal + "\"";
		searchParameters.setQuery(query);
		searchParameters.setMaxItems(1);
		searchParameters.setLimit(1);
		searchParameters.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
		List<NodeRef> nodeRefs = getSearchService().query(searchParameters).getNodeRefs();
		if (nodeRefs.size() == 1) {
			return nodeRefs.get(0);
		}
		return null;
	}

	/**
	 * Execute a SelectNodes XPath search.
	 * 
	 * @param search
	 *            SelectNodes XPath search string to execute
	 * @return JavaScript array of Node results from the search - can be empty
	 *         but not null
	 */
	public List<NodeRef> getNodesByXPath(String search) {
		if ((search != null) && (search.length() != 0)) {
			try {
				return searchService.selectNodes(nodeService.getRootNode(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE),
						search, null, getNamespaceResolver(), false);
			} catch (Exception err) {
				throw new SEIPRuntimeException("Failed to execute search: " + search, err);
			}

		}
		return null;

	}

	/**
	 * Gets the case documents. FIXME - on change of model
	 * 
	 * @param updateNode
	 *            the update node
	 * @return the case documents
	 */
	public List<NodeRef> getCaseDocuments(NodeRef updateNode) {
		return getNodesAspects(DOCUMENT_ASPECTS, buildPath(getNodePath(updateNode), true));
	}

	/**
	 * Gets the case documents by iterating over db entries. FIXME - on change
	 * of model
	 * 
	 * @param updateable
	 *            is the node for the case
	 * @return the documents for the case
	 */
	public List<NodeRef> getCaseDocumentsFromDB(NodeRef updateable) {
		List<NodeRef> children = new LinkedList<NodeRef>();
		Collection<ChildAssociationRef> childAssocsWithoutParentAssocsOfType = nodeService
				.getChildAssocsWithoutParentAssocsOfType(updateable, ContentModel.ASSOC_CONTAINS);
		for (ChildAssociationRef childAssociationRef : childAssocsWithoutParentAssocsOfType) {
			Collection<ChildAssociationRef> docRefs = nodeService.getChildAssocsWithoutParentAssocsOfType(
					childAssociationRef.getChildRef(), ContentModel.ASSOC_CONTAINS);
			for (ChildAssociationRef docRef : docRefs) {
				// check if it is doc
				if (nodeService.hasAspect(docRef.getChildRef(), CMFModel.ASPECT_CMF_CASE_STRUCTURED_DOC)
						|| nodeService.hasAspect(docRef.getChildRef(), CMFModel.ASPECT_CMF_CASE_ATTACHED_DOC)) {
					children.add(docRef.getChildRef());
				}
			}
		}

		return children;
	}

	/**
	 * Gets the node path.
	 * 
	 * @param updateNode
	 *            the update node
	 * @return the node path
	 */
	public String getNodePath(NodeRef updateNode) {

		return nodeService.getPath(updateNode).toPrefixString(getNamespaceResolver());
	}

	/**
	 * Gets the namespace resolver.
	 *
	 * @return the namespace resolver
	 */
	private NamespaceService getNamespaceResolver() {
		if (namespaceService == null) {
			namespaceService = serviceRegistry.getNamespaceService();
		}
		return namespaceService;
	}

	/**
	 * Child by name path.
	 * 
	 * @param parent
	 *            the parent
	 * @param path
	 *            the path
	 * @return the node ref
	 */
	public NodeRef childByNamePath(NodeRef parent, String path) {
		NodeRef child = null;
		QName type = nodeService.getType(parent);
		if (serviceRegistry.getDictionaryService().isSubClass(type, ContentModel.TYPE_FOLDER)) {
			/**
			 * The current node is a folder. optimized code path for cm:folder
			 * and sub-types supporting getChildrenByName() method
			 */
			NodeRef result = null;
			StringTokenizer t = new StringTokenizer(path, "/");
			if (t.hasMoreTokens()) {
				result = parent;
				while (t.hasMoreTokens() && (result != null)) {
					String name = t.nextToken();
					try {
						result = nodeService.getChildByName(result, ContentModel.ASSOC_CONTAINS, name);
					} catch (AccessDeniedException ade) {
						LOGGER.warn("Access denied for path :" + path, ade);
						result = null;
					}
				}
			}

			child = result;
		} else {
			/**
			 * The current node is not a folder. Perhaps it is Company Home ?
			 */
			// convert the name based path to a valid XPath query
			StringBuilder xpath = new StringBuilder(path.length() << 1);
			StringTokenizer t = new StringTokenizer(path, "/");
			int count = 0;
			QueryParameterDefinition[] params = new QueryParameterDefinition[t.countTokens()];
			DataTypeDefinition ddText = serviceRegistry.getDictionaryService().getDataType(DataTypeDefinition.TEXT);
			NamespaceService ns = getNamespaceResolver();
			while (t.hasMoreTokens()) {
				if (xpath.length() != 0) {
					xpath.append('/');
				}
				String strCount = Integer.toString(count);
				xpath.append("*[@cm:name=$cm:name").append(strCount).append(']');
				params[count++] = new QueryParameterDefImpl(
						QName.createQName(NamespaceService.CONTENT_MODEL_PREFIX, "name" + strCount, ns), ddText, true,
						t.nextToken());
			}

		}

		return child;
	}

	/**
	 * Search nodes by some custom query (should be valid) and set of paths.
	 * 
	 * @param queryBase
	 *            some basic query (syntax is not checked)
	 * @param paths
	 *            the paths
	 * @return the list
	 */
	public List<NodeRef> searchNodes(String queryBase, Set<String> paths) {
		if (queryBase == null) {
			throw new SEIPRuntimeException("Query is not valid!");
		}
		// optimized search
		StringBuilder query = new StringBuilder();
		query.append("( ").append(queryBase).append(") ");
		if ((paths != null) && (!paths.isEmpty())) {
			query.append(" AND (");
			int index = paths.size();
			for (String string : paths) {
				query.append("PATH:\"").append(string).append("\"");
				index--;
				if (index > 0) {
					query.append(" OR ");
				}
			}
			query.append(" ) ");
		}

		return search(query.toString());
	}

	/**
	 * Execute search using the provided query.
	 * 
	 * @param query
	 *            the query to execute
	 * @return the list of found nodes.
	 */
	public List<NodeRef> search(String query) {
		SearchParameters searchParameters = new SearchParameters();
		searchParameters.setLanguage(getServiceProxy().getDefaultQueryLanguage());
		if (TRACE_ENABLED) {
			LOGGER.trace("Debug Query: " + query);
		}
		searchParameters.setQuery(query);
		searchParameters.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
		return getSearchService().query(searchParameters).getNodeRefs();
	}

	/**
	 * Execute search using the provided query.
	 * 
	 * @param context
	 *            the site id or null
	 * @param query
	 *            the query to execute
	 * @param paging
	 *            is the paging object
	 * @param sort
	 *            is the sorting object
	 * @param additinalArgs
	 *            are some other args (not used by now)
	 * @return the list of found nodes.
	 * @throws JSONException
	 *             on json error
	 */
	public Pair<List<NodeRef>, Map<String, Object>> search(Pair<String, String> context, String query,
			JSONObject paging, JSONArray sort, JSONObject additinalArgs) throws JSONException {
		SearchParameters searchParameters = new SearchParameters();
		searchParameters.setLanguage(getServiceProxy().getDefaultQueryLanguage());
		StringBuilder queryTemp = new StringBuilder();
		JSONObject additinalArguments = additinalArgs;
		if (additinalArguments == null) {
			additinalArguments = new JSONObject();
		}
		// set the context path
		if (context != null && context.getSecond() != null && query.length() > 0) {
			if (additinalArguments != null && additinalArguments.has("FILTER")) {
				queryTemp.append(additinalArguments.getString("FILTER")).append(" AND (");
			} else {
				queryTemp.append("(");
			}
			queryTemp.append(context.getFirst()).append(":\"").append(context.getSecond()).append("\" AND ")
					.append("( ").append(query).append("))");

		} else {
			queryTemp.append(query);
		}
		searchParameters.setQuery(queryTemp.toString());
		searchParameters.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
		// add the sort args
		if (sort != null) {
			for (int i = 0; i < sort.length(); i++) {
				JSONObject sorter = sort.getJSONObject(i);
				if (sorter.length() != 1) {
					throw new SEIPRuntimeException("Sorting element at [" + i + "] is not valid!");
				}
				String key = sorter.keys().next().toString();
				QName sortField = QName.resolveToQName(getNamespaceResolver(), key);
				if (sortField != null) {
					searchParameters.addSort("@" + sortField, sorter.getBoolean(key));
				}
			}
		}
		int skip = 0;
		int total = -1;
		int max = 0;
		int pageSize = 0;
		// add the paging args
		if (paging != null) {
			if (paging.has(KEY_PAGING_TOTAL) && (paging.getInt(KEY_PAGING_TOTAL) > 0)) {
				total = paging.getInt(KEY_PAGING_TOTAL);
			} else {
				max = paging.getInt("maxSize");
			}
			// page size always should be provided
			pageSize = paging.getInt("pageSize");
			// set the paging
			if (paging.has("skip")) {
				skip = paging.getInt("skip");
				if (skip > 0) {
					searchParameters.setSkipCount(skip);
				}
			}

			//
			// if total is set already
			if ((pageSize > 0) && (total > -1)) {
				searchParameters.setMaxItems(pageSize);
			} else {
				searchParameters.setMaxItems(max);
			}

		}

		if ((additinalArguments != null) && additinalArguments.has(KEY_KEYWORDS)) {
			Map<String, String> queryTemplates = QUERY_TEMPLATES.get(0);
			searchParameters.addQueryTemplate(queryTemplates.get("field"), queryTemplates.get("template"));
			searchParameters.setDefaultFieldName(KEY_KEYWORDS);
		}
		List<NodeRef> nodeRefs = getSearchService().query(searchParameters).getNodeRefs();
		int foundNodes = nodeRefs.size();
		if (total < 0) {
			total = foundNodes;
		}
		if (DEBUG_ENABLED) {
			LOGGER.debug(searchParameters.getQuery() + "  ->  Found: " + foundNodes + " " + nodeRefs);
		}
		if (paging == null) {
			return new Pair<List<NodeRef>, Map<String, Object>>(nodeRefs, ModelUtil.buildPaging(total, pageSize, skip));
		}
		if ((pageSize + skip) < foundNodes) {
			return new Pair<List<NodeRef>, Map<String, Object>>(nodeRefs.subList(skip, skip + pageSize),
					ModelUtil.buildPaging(total, pageSize, skip));
		}

		return new Pair<List<NodeRef>, Map<String, Object>>(nodeRefs, ModelUtil.buildPaging(total, pageSize, skip));
	}

	/**
	 * Gets the nodes.
	 * 
	 * @param type
	 *            the type
	 * @param path
	 *            the path
	 * @return the nodes
	 */
	private List<NodeRef> getNodesAspects(Set<String> type, String path) {
		HashSet<String> hashSet = new HashSet<String>();
		hashSet.add(path);
		int index = type.size();
		StringBuilder query = new StringBuilder();
		for (String aspect : type) {
			query.append("ASPECT:\"").append(aspect).append("\"");
			index--;
			if (index > 0) {
				query.append(" OR ");

			}
		}
		return searchNodes(query.toString(), hashSet);
	}

	/**
	 * Gets the nodes.
	 * 
	 * @param type
	 *            the type
	 * @param path
	 *            the path
	 * @return the nodes
	 */
	private List<NodeRef> getNodesForType(String type, String path) {
		Set<String> hashSet = new HashSet<String>();
		hashSet.add(path);
		StringBuilder query = new StringBuilder();
		query.append("TYPE:\"").append(type).append("\"");
		return searchNodes(query.toString(), hashSet);
	}

	/**
	 * Get child by xpath relative to node.
	 * 
	 * @param relativeParent
	 *            is the relative parent
	 * @param path
	 *            is the path
	 * @return the found node (single one) or null otherwise
	 */
	public NodeRef getNodeByPath(NodeRef relativeParent, String path) {
		// optimized search
		if (DEBUG_ENABLED) {
			LOGGER.debug("getNodeByPath():  " + path);
		}
		List<NodeRef> nodes = getSearchService().selectNodes(relativeParent, path, null, getNamespaceResolver(), true);
		if (nodes.size() == 1) {
			return nodes.get(0);
		}
		return null;
	}

	/**
	 * Gets the node children by path.
	 * 
	 * @param path
	 *            the path
	 * @param recursive
	 *            whether to search all levels
	 * @return the node children by path
	 * @throws JSONException
	 *             the jSON exception
	 */
	public List<NodeRef> getNodeChildrenByPath(String path, boolean recursive) throws JSONException {
		// optimized search
		SearchParameters searchParameters = new SearchParameters();
		searchParameters.setLanguage(getServiceProxy().getDefaultQueryLanguage());
		searchParameters.setQuery("PATH:\"" + buildPath(path, recursive) + "\"");
		searchParameters.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
		return getSearchService().query(searchParameters).getNodeRefs();
	}

	/**
	 * Builds the path.
	 * 
	 * @param path
	 *            the path
	 * @param recursive
	 *            the recursive
	 * @return the string
	 */
	private String buildPath(String path, boolean recursive) {
		String pathLocal = path;
		String suffix = recursive ? "/*" : "*";
		if (path.endsWith("/")) {
			pathLocal += suffix;
		} else {
			pathLocal += "/" + suffix;
		}
		return pathLocal;
	}

	/**
	 * Creates the cmf definitions space if not exists.
	 * 
	 * @param basePath
	 *            the base path - some site or companyhome
	 * @param type
	 *            the type
	 * @param properties
	 *            the properties
	 * @param throwExceptionOnDuplicate
	 *            whether to indicate duplicate child
	 * @return the node ref
	 */
	public NodeRef createCMFSpace(NodeRef basePath, QName type, Map<QName, Serializable> properties,
			boolean throwExceptionOnDuplicate) {
		QName containerBaseType = getNodeService().getType(basePath);
		QName containType = ContentModel.TYPE_CONTAINER.equals(containerBaseType) ? ContentModel.ASSOC_CHILDREN
				: ContentModel.ASSOC_CONTAINS;
		Serializable name = properties.get(ContentModel.PROP_NAME);
		if (name == null) {
			throw new SEIPRuntimeException("Missing argument cm:name in properties");
		}
		name = name.toString().replaceAll("\\s+", "");
		NodeRef nodeByPath = getNodeByPath(basePath,
				NamespaceService.CONTENT_MODEL_PREFIX + ":" + ISO9075.encode(name.toString()));
		// if not exists
		if (nodeByPath == null) {
			QName nameForAssoc = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, name.toString());
			nodeByPath = getNodeService().createNode(basePath, containType, nameForAssoc, type, properties)
					.getChildRef();
			return nodeByPath;
		}
		if (throwExceptionOnDuplicate) {
			throw new WebScriptException(500, "The requested node " + name + " already exists: ");
		}
		return nodeByPath;
	}

	/**
	 * Creates the cmf instance space if not exists.
	 * 
	 * @param basePath
	 *            the instances path
	 * @param properties
	 *            the properties
	 * @return the node ref
	 * @throws JSONException
	 *             the jSON exception
	 */
	public NodeRef createCMFCaseSpace(NodeRef basePath, final Map<QName, Serializable> properties)
			throws JSONException {
		NodeRef basePathNew = getWorkingDir(basePath);
		return createCMFSpace(basePathNew, CMFModel.TYPE_CMF_CASE_SPACE, properties, true);
	}

	/**
	 * Gets the working dir for current case.
	 * 
	 * @param basePath
	 *            the base path
	 * @return the working dir /yyyy/MM/dd
	 */
	public NodeRef getWorkingDir(final NodeRef basePath) {
		Calendar calendar = Calendar.getInstance();
		final String year = Integer.toString(calendar.get(Calendar.YEAR));
		final String month = Integer.toString(calendar.get(Calendar.MONTH) + 1);
		final String day = Integer.toString(Calendar.DAY_OF_MONTH);
		try {
			spaceCreationLock.writeLock().lock();
			NodeRef childRef = getOrCreateChild(basePath, year);
			childRef = getOrCreateChild(childRef, month);
			return getOrCreateChild(childRef, day);
		} finally {
			spaceCreationLock.writeLock().unlock();
		}
	}

	/**
	 * Creates the cmf section space.
	 * 
	 * @param caseSpace
	 *            the case space
	 * @param props
	 *            the props
	 * @return the node ref
	 */
	public NodeRef createCMFSectionSpace(NodeRef caseSpace, Map<QName, Serializable> props) {
		return createCMFSpace(caseSpace, CMFModel.TYPE_CMF_CASE_SECTION_SPACE, props, true);
	}

	/**
	 * Creates the cmf instances space.
	 * 
	 * @param basePath
	 *            the base path
	 * @param properties
	 *            the properties
	 * @return the node ref
	 * @throws JSONException
	 *             the jSON exception
	 */
	public NodeRef createCMFInstancesSpace(NodeRef basePath, Map<QName, Serializable> properties) throws JSONException {
		return createCMFSpace(basePath, CMFModel.TYPE_CMF_CASE_INSTANCES_SPACE, properties, false);
	}

	/**
	 * Creates the cmf definitions space.
	 * 
	 * @param basePath
	 *            the base path
	 * @param properties
	 *            the properties
	 * @return the node ref
	 * @throws JSONException
	 *             the jSON exception
	 */
	public NodeRef createCMFCaseDefinitionSpace(NodeRef basePath, Map<QName, Serializable> properties)
			throws JSONException {
		return createCMFSpace(basePath, CMFModel.TYPE_CMF_CASE_DEF_SPACE, properties, false);
	}

	/**
	 * Creates the cmf document definitions space.
	 * 
	 * @param basePath
	 *            the base path
	 * @param properties
	 *            the properties
	 * @return the node ref
	 * @throws JSONException
	 *             the jSON exception
	 */
	public NodeRef createCMFDocumentDefinitionSpace(NodeRef basePath, Map<QName, Serializable> properties)
			throws JSONException {
		return createCMFSpace(basePath, CMFModel.TYPE_CMF_DOCUMENT_DEF_SPACE, properties, false);
	}

	/**
	 * Creates the cmf workflow space.
	 * 
	 * @param basePath
	 *            the base path
	 * @param properties
	 *            the properties
	 * @return the node ref
	 * @throws JSONException
	 *             the jSON exception
	 */
	public NodeRef createCMFWorkflowDefinitionSpace(NodeRef basePath, Map<QName, Serializable> properties)
			throws JSONException {
		return createCMFSpace(basePath, CMFModel.TYPE_CMF_WORKFLOW_DEF_SPACE, properties, false);
	}

	/**
	 * Creates the cmf workflow task space.
	 * 
	 * @param basePath
	 *            the base path
	 * @param properties
	 *            the properties
	 * @return the node ref
	 * @throws JSONException
	 *             the jSON exception
	 */
	public NodeRef createCMFWorkflowTaskDefinitionSpace(NodeRef basePath, Map<QName, Serializable> properties)
			throws JSONException {
		return createCMFSpace(basePath, CMFModel.TYPE_CMF_TASK_DEF_SPACE, properties, false);
	}

	/**
	 * Gets the or creates child folder based on the provided name.
	 * 
	 * @param parent
	 *            the parent
	 * @param name
	 *            the name
	 * @return the or create child
	 */
	private NodeRef getOrCreateChild(NodeRef parent, String name) {
		NodeRef childByName = getNodeService().getChildByName(parent, ContentModel.ASSOC_CONTAINS, name);
		if (childByName == null) {
			Map<QName, Serializable> props = new HashMap<QName, Serializable>(1);
			props.put(ContentModel.PROP_NAME, name);
			ChildAssociationRef createNode = getNodeService().createNode(parent, ContentModel.ASSOC_CONTAINS,
					QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, name), ContentModel.TYPE_FOLDER, props);
			return createNode.getChildRef();
		}
		return childByName;
	}

	/**
	 * Gets the cMF case definition space.
	 * 
	 * @param basePathNode
	 *            the base path node
	 * @return the cMF case definition space
	 */
	public NodeRef getCMFCaseDefinitionSpace(NodeRef basePathNode) {
		String basePath = nodeService.getPath(basePathNode).toPrefixString(getNamespaceResolver());
		return getCMFSpace(CMFModel.TYPE_CMF_CASE_DEF_SPACE, basePath);
	}

	/**
	 * Gets the cMF case definition space.
	 * 
	 * @param basePath
	 *            the base path
	 * @return the cMF case definition space
	 */
	public NodeRef getCMFCaseDefinitionSpace(String basePath) {

		return getCMFSpace(CMFModel.TYPE_CMF_CASE_DEF_SPACE, basePath);
	}

	/**
	 * Gets the node ref.
	 * 
	 * @param nodeId
	 *            the node id
	 * @return the noderef or null if could not be found
	 */
	public NodeRef getNodeRef(String nodeId) {
		if (nodeId != null) {
			String[] split = nodeId.replace(":/", "").split("/");
			if (split.length == 3) {
				return repository.findNodeRef("node", split);
			} else if (split.length == 1) {
				return repository.findNodeRef("node", new String[] { "workspace", "SpacesStore", split[0] });
			}
		}
		return null;

	}

	/**
	 * Gets the cMF case instance space.
	 * 
	 * @param basePathNode
	 *            the base path node
	 * @return the cMF case instance space
	 */
	public NodeRef getCMFCaseInstanceSpace(NodeRef basePathNode) {
		return getCMFSpace(CMFModel.TYPE_CMF_CASE_INSTANCES_SPACE, basePathNode);
	}

	/**
	 * Gets the cMF case instance space.
	 * 
	 * @param basePath
	 *            the base path
	 * @return the cMF case instance space
	 */
	public NodeRef getCMFCaseInstanceSpace(String basePath) {

		return getCMFSpace(CMFModel.TYPE_CMF_CASE_INSTANCES_SPACE, basePath);
	}

	/**
	 * Enrich path to support search on any level deeper.
	 *
	 * @param rootPath
	 *            is the path to enrich
	 * @return the enriched path
	 */
	public String enrichPath(String rootPath) {
		if (!rootPath.endsWith("/")) {
			return rootPath + "//*";
		} else {
			return rootPath + "/*";
		}
	}

	/**
	 * Gets the cmf space using base node.
	 *
	 * @param type
	 *            the type
	 * @param basePath
	 *            the base path
	 * @return the cMF case definition space
	 */
	public NodeRef getCMFSpace(QName type, String basePath) {
		String rootPath = getRootPath(basePath);
		return getCMFSpaceInternal(type, rootPath);
	}

	/**
	 * Gets the cmf space using base path.
	 *
	 * @param type
	 *            the type
	 * @param basePath
	 *            the base path
	 * @return the cMF case definition space
	 */
	public NodeRef getCMFSpace(QName type, NodeRef basePath) {
		String rootPath = getRootPath(basePath);
		return getCMFSpaceInternal(type, rootPath);
	}

	/**
	 * Gets the CMF space internal.
	 *
	 * @param type
	 *            the type
	 * @param rootPath
	 *            the root path
	 * @return the CMF space internal
	 */
	private NodeRef getCMFSpaceInternal(QName type, String rootPath) {
		if (rootPath == null) {
			throw new SEIPRuntimeException("Invalid location provided");
		}
		String rootPathLocal = enrichPath(rootPath);
		List<NodeRef> nodes = getNodesForType(type.toPrefixString(getServiceRegistry().getNamespaceService()),
				rootPathLocal);
		if (nodes.size() != 1) {
			throw new SEIPRuntimeException("Expected space is not found or duplicated! " + nodes.size());
		}
		return nodes.get(0);
	}

	/**
	 * Gets the root path.
	 * 
	 * @param basePathString
	 *            the base path string
	 * @return the root path
	 */
	private String getRootPath(String basePathString) {
		NodeRef basePathNode = getNodeByPath(basePathString);
		if (basePathNode == null) {
			throw new SEIPRuntimeException("Location for provided argument could not be retrieved!");
		}
		return getRootPath(basePathNode);
	}

	/**
	 * Gets the root container for node. if node is in the site - the site
	 * itself, if node is in repository - current path (TODO - may be the root)
	 * 
	 * @param basePathNode
	 *            the node to investigate
	 * @return the root path
	 */
	private String getRootPath(NodeRef basePathNode) {
		SiteInfo info = serviceRegistry.getSiteService().getSite(basePathNode);
		String basePath = null;
		if (info != null) {
			basePath = nodeService.getPath(info.getNodeRef()).toPrefixString(getNamespaceResolver());
		} else {
			basePath = nodeService.getPath(basePathNode).toPrefixString(getNamespaceResolver());
		}
		return basePath;
	}

	/**
	 * Retrieves child by {@link ContentModel#PROP_NAME} value for assocations
	 * allowing duplicate name childs. <strong> First result is
	 * returned</strong>
	 * 
	 * @param parent
	 *            is the parent node
	 * @param name
	 *            is the value for {@link ContentModel#PROP_NAME}
	 * @return the found node or null
	 */
	public NodeRef getChildFolderByName(NodeRef parent, String name) {
		return nodeService.getChildByName(parent, ContentModel.ASSOC_CONTAINS, name);
	}

	/**
	 * Gets the child container by name.
	 * 
	 * @param parent
	 *            the parent
	 * @param name
	 *            the name
	 * @return the child container by name
	 */
	public NodeRef getChildContainerByName(NodeRef parent, String name) {
		final List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(parent);
		for (final ChildAssociationRef childAssociationRef : childAssocs) {
			final NodeRef taskNodeCurrent = childAssociationRef.getChildRef();
			if (name.equals(nodeService.getProperty(taskNodeCurrent, ContentModel.PROP_NAME))) {
				return taskNodeCurrent;
			}
		}
		return null;
	}

	/**
	 * Converts json key to fully quilified qname pair (keys should be valid
	 * qname).
	 * 
	 * @param key
	 *            is the key
	 * @param value
	 *            is the value
	 * @return the converted pair or null on error
	 */
	protected Pair<QName, Serializable> toFullNotation(String key, Serializable value) {

		QName resolvedToQName = QName.resolveToQName(getNamespaceResolver(), key);
		if (resolvedToQName != null) {
			PropertyDefinition property = serviceRegistry.getDictionaryService().getProperty(resolvedToQName);
			if (DEBUG_ENABLED) {
				LOGGER.debug("toMap() " + property + " for: " + resolvedToQName);
			}
			if ((property != null) && "java.util.Date".equals(property.getDataType().getJavaClassName())) {
				return new Pair<QName, Serializable>(resolvedToQName, new Date(Long.parseLong(value.toString())));
			} else {
				return new Pair<QName, Serializable>(resolvedToQName, value);
			}

		} else {
			LOGGER.warn("toMap() SKIP as could not be resolved! " + key);
		}
		return null;
	}

	/**
	 * Invoke ldap synchronize using the default configs.
	 * 
	 * @param tenantId
	 *            is the tenant it initiate an ldap synch for. If null full ldap
	 *            synch is made
	 */
	public void reloadLDAP(String tenantId) {
		// try to force the user synchronization
		if (tenantId != null && !tenantId.isEmpty()) {
			Method findMethod = ReflectionUtils.findMethod(chainingUserRegistrySynchronizer.getClass(), "synchronize", String.class, boolean.class,
					boolean.class, boolean.class);
			if (findMethod != null) {
				ReflectionUtils.invokeMethod(findMethod, chainingUserRegistrySynchronizer, tenantId, true, false, true);
				return;
			}
		}
		Method findMethod = ReflectionUtils.findMethod(chainingUserRegistrySynchronizer.getClass(), "synchronize",
				boolean.class, boolean.class, boolean.class);

		ReflectionUtils.invokeMethod(findMethod, chainingUserRegistrySynchronizer, true, false, true);

	}

	/**
	 * See {@link SEIPTenantIntegration#getSystemUser(String)}
	 * 
	 * @param userName
	 *            is the user to check
	 * @return the system user
	 */
	public static String getSystemUser(String userName) {
		return SEIPTenantIntegration.getSystemUser(userName);
	}

	/**
	 * Gets the system user using current runAs user See
	 * {@link SEIPTenantIntegration#getSystemUser()}
	 * 
	 * @return the system user
	 */
	public static String getSystemUser() {
		return SEIPTenantIntegration.getSystemUser();
	}

	/**
	 * Gets the system user using current runAs user
	 *
	 * @return the system user
	 */
	public static String getTenantId() {
		return SEIPTenantIntegration.getTenantId(AuthenticationUtil.getRunAsUser());
	}

	/**
	 * Extract the tenant Id from userName
	 * 
	 * @param userName
	 *            to get for
	 * @return the tenant id or emtpy string if this is default tenant
	 */
	public static String getTenantId(String userName) {
		return SEIPTenantIntegration.getTenantId(userName);
	}

	// ----------------SETTERS/GETTERS------------------//
	/**
	 * Gets the search service.
	 * 
	 * @return the searchService
	 */
	public SearchService getSearchService() {
		return searchService;
	}

	/**
	 * Sets the search service.
	 * 
	 * @param searchService
	 *            the searchService to set
	 */
	public void setSearchService(SearchService searchService) {
		this.searchService = searchService;
	}

	/**
	 * Gets the node service.
	 * 
	 * @return the nodeService
	 */
	public NodeService getNodeService() {
		return nodeService;
	}

	/**
	 * Sets the node service.
	 * 
	 * @param nodeService
	 *            the nodeService to set
	 */
	public void setNodeService(NodeService nodeService) {
		this.nodeService = nodeService;
	}

	/**
	 * Gets the service registry.
	 * 
	 * @return the serviceRegistry
	 */
	public ServiceRegistry getServiceRegistry() {
		return serviceRegistry;
	}

	/**
	 * Sets the service registry.
	 * 
	 * @param serviceRegistry
	 *            the serviceRegistry to set
	 */
	public void setServiceRegistry(ServiceRegistry serviceRegistry) {
		this.serviceRegistry = serviceRegistry;
	}

	/**
	 * Gets the cmf lock service.
	 * 
	 * @return the cmfLockService
	 */
	public CMFLockService getCmfLockService() {
		return cmfLockService;
	}

	/**
	 * Sets the cmf lock service.
	 * 
	 * @param cmfLockService
	 *            the cmfLockService to set
	 */
	public void setCmfLockService(CMFLockService cmfLockService) {
		this.cmfLockService = cmfLockService;
	}

	/**
	 * Gets the repository.
	 * 
	 * @return the repository
	 */
	public Repository getRepository() {
		return repository;
	}

	/**
	 * Sets the repository.
	 * 
	 * @param repository
	 *            the repository to set
	 */
	public void setRepository(Repository repository) {
		this.repository = repository;
	}

	/**
	 * Gets the service proxy.
	 * 
	 * @return the serviceProxy
	 */
	public ServiceProxy getServiceProxy() {
		if (serviceProxy == null) {
			serviceProxy = (ServiceProxy) serviceRegistry
					.getService(QName.createQName(NamespaceService.ALFRESCO_URI, "ServiceProxy"));
		}
		return serviceProxy;
	}

	/**
	 * Sets the service proxy.
	 * 
	 * @param serviceProxy
	 *            the serviceProxy to set
	 */
	public void setServiceProxy(ServiceProxy serviceProxy) {
		this.serviceProxy = serviceProxy;
	}

	/**
	 * Getter method for chainingUserRegistrySynchronizer.
	 *
	 * @return the chainingUserRegistrySynchronizer
	 */
	public Object getChainingUserRegistrySynchronizer() {
		return chainingUserRegistrySynchronizer;
	}

	/**
	 * Setter method for chainingUserRegistrySynchronizer.
	 *
	 * @param chainingUserRegistrySynchronizer
	 *            the chainingUserRegistrySynchronizer to set
	 */
	public void setChainingUserRegistrySynchronizer(Object chainingUserRegistrySynchronizer) {
		this.chainingUserRegistrySynchronizer = chainingUserRegistrySynchronizer;
	}

}
