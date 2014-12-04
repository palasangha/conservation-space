/**
 *
 */
package com.sirma.itt.cmf.integration.webscript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.ModelUtil;
import org.alfresco.util.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;

import com.sirma.itt.cmf.integration.model.CMFModel;

/**
 * Script that will executes custom searches.
 *
 * @author borislav banchev
 */
public class SearchScript extends BaseAlfrescoScript {
	// cache the path, to reduce db load
	private static final Map<String, String> CACHED_PATHS = new HashMap<String, String>(50);

	/**
	 * Execute internal. Wrapper for system user action.
	 *
	 * @param req
	 *            the original request
	 * @return the updated model
	 */
	@Override
	protected Map<String, Object> executeInternal(WebScriptRequest req) {
		Map<String, Object> model = new HashMap<String, Object>(2);
		model.put("mode", "list");
		Pair<List<NodeRef>, Map<String, Object>> nodesData = null;
		List<NodeRef> nodeRefs = null;
		try {
			String content = req.getContent().getContent();
			JSONObject request = new JSONObject(content);
			// specific search for cases
			String servicePath = req.getServicePath();
			Pair<String, String> context = null;
			if (servicePath.endsWith("/cmf/search")) {
				// general search
				JSONObject paging = null;
				debug("Request: ", request);
				if (request.has("paging")) {
					paging = request.getJSONObject("paging");
				}
				JSONObject additional = null;
				if (request.has("keywords")) {
					additional = new JSONObject();
					additional.put("keywords", request.get("keywords"));
				}
				JSONArray sort = null;
				if (request.has("sort")) {
					sort = request.getJSONArray("sort");
				}
				// context restriction
				context = getContexteParam(request);

				nodesData = cmfService.search(context, request.getString(KEY_QUERY), paging, sort,
						additional);
			} else if (servicePath.contains("/cmf/search/instances")) {
				nodeRefs = searchByAspect(request, CMFModel.ASPECT_CMF_CASE_INSTANCE);
			} else if (servicePath.contains("/cmf/search/containers/cmf")) {
				// specific search for definitions
				List<NodeRef> nodeRefsSpaces = cmfService
						.search("PATH:\"/app:company_home/st:sites//*\" AND TYPE:\""
								+ CMFModel.TYPE_CMF_CASE_INSTANCES_SPACE.toString() + "\"");
				nodeRefs = new ArrayList<NodeRef>(nodeRefsSpaces.size());
				SiteService siteService = getServiceRegistry().getSiteService();
				for (NodeRef nodeRef : nodeRefsSpaces) {
					nodeRefs.add(siteService.getSite(nodeRef).getNodeRef());
				}
			} else if (servicePath.contains("/cmf/search/case/documents")) {
				// // case document search
				// JSONObject paging = null;
				// // if (request.has("paging")) {
				// // paging = request.getJSONObject("paging");
				// // }
				// JSONArray sort = null;
				// if (request.has("sort")) {
				// sort = request.getJSONArray("sort");
				// }
				// // get first pass nodes
				// Pair<List<NodeRef>, Integer> nodesData = caseService.search(
				// request.getString(KEY_QUERY), paging, sort, null);
				// String parentQuery = nodeRefs.toString().replaceAll(", ",
				// "\" OR PARENT:\"");
				// parentQuery = parentQuery.replace("[",
				// "PARENT:\"").replace("]", "\"");
				// // if (request.has("keywords")) {
				// // model.put("mode", "map");
				// // JSONObject additional = new JSONObject();
				// // additional.put("keywords", request.get("keywords"));
				// // nodeRefs = caseService.search(parentQuery +
				// // " AND TYPE:\"cmf:\" ", paging,
				// // sort, additional);
				// // } else {
				// Pair<List<NodeRef>, Integer> nodesData = caseService.search(
				// request.getString(KEY_QUERY), paging, sort, null);
				// // }
				//
				// model.put("results", nodeRefs);
			} else {
				// general search
				JSONObject paging = null;
				debug("Request: ", request);
				if (request.has("paging")) {
					paging = request.getJSONObject("paging");
				}
				JSONObject additional = null;
				if (request.has("keywords")) {
					additional = new JSONObject();
					additional.put("keywords", request.get("keywords"));
				}
				JSONArray sort = null;
				if (request.has("sort")) {
					sort = request.getJSONArray("sort");
				}
				// context restriction
				context = getContexteParam(request);

				nodesData = cmfService.search(context, request.getString(KEY_QUERY), paging, sort,
						additional);
			}
			if (nodesData == null) {
				nodesData = new Pair<List<NodeRef>, Map<String, Object>>(nodeRefs,
						ModelUtil.buildPaging(nodeRefs.size(), -1, 0));
			}
			model.put("results", nodesData.getFirst());
			model.put("paging", nodesData.getSecond());
		} catch (Exception e) {
			// StringWriter out = new StringWriter();
			// e.printStackTrace(new PrintWriter(out));
			throw new WebScriptException(e.getMessage(), e);
		}
		return model;
	}

	/**
	 * Generate path for context id, if argument is provided, return null if
	 * site is invalid or not provided
	 *
	 * @param request
	 *            is the http request
	 * @return the path
	 * @throws JSONException
	 *             on parse json error
	 */
	private Pair<String, String> getContexteParam(JSONObject request) throws JSONException {
		if (!request.has(KEY_CONTEXT)) {
			return null;
		}
		String siteId = request.getString(KEY_CONTEXT);
		String path = null;
		if (CACHED_PATHS.containsKey(CACHED_PATHS)) {
			path = CACHED_PATHS.get(siteId);
		} else {
			SiteInfo site = getServiceRegistry().getSiteService().getSite(siteId);
			if (site == null) {
				LOGGER.error("Invalid context: " + siteId + ". Should be valid site");
				return null;
			} else {
				path = nodeService.getPath(site.getNodeRef()).toPrefixString(
						serviceRegistry.getNamespaceService());
				path = cmfService.enrichPath(path);
			}
			CACHED_PATHS.put(siteId, path);
		}
		return new Pair<String, String>("PATH", path);
	}

	/**
	 * Search by aspect.
	 *
	 * @param request
	 *            the request
	 * @param aspect
	 *            the aspect
	 * @return the list
	 * @throws JSONException
	 *             the jSON exception
	 */
	protected List<NodeRef> searchByAspect(JSONObject request, QName aspect) throws JSONException {
		List<NodeRef> nodeRefs;
		Set<String> paths = new HashSet<String>();
		if (request.has(KEY_START_PATH)) {
			paths.add(request.getString(KEY_START_PATH));
		} else if (request.has(KEY_SITES_IDS)) {
			String string = request.getString(KEY_SITES_IDS);
			String[] sites = string.split(",");
			for (String site : sites) {
				SiteInfo siteInfo = getServiceRegistry().getSiteService().getSite(site);
				if (siteInfo != null) {
					// add each site
					paths.add(nodeService.getPath(siteInfo.getNodeRef()).toPrefixString(
							serviceRegistry.getNamespaceService())
							+ "/*/*");// append one dir deeper
				}
			}
		}
		String query = "";
		if (request.has(KEY_QUERY)) {
			query = " ( " + request.getString(KEY_QUERY) + " ) AND ";
		}
		if (!paths.isEmpty()) {
			nodeRefs = getCaseService().searchNodes(query + "ASPECT:\"" + aspect.toString() + "\"",
					paths);
		} else {
			nodeRefs = getCaseService().searchNodes(query + "ASPECT:\"" + aspect.toString() + "\"",
					null);
		}
		return nodeRefs;
	}

}
