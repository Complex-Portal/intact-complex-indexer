package uk.ac.ebi.intact.services.search.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.myfaces.orchestra.viewController.annotations.ViewController;
import org.apache.myfaces.orchestra.viewController.annotations.PreRenderView;
import org.apache.myfaces.orchestra.conversation.ConversationUtils;
import org.apache.myfaces.trinidad.component.UIXTable;
import org.apache.myfaces.trinidad.component.core.data.CoreSelectRangeChoiceBar;
import org.apache.myfaces.trinidad.event.PollEvent;
import org.apache.myfaces.trinidad.event.RangeChangeEvent;
import org.apache.myfaces.trinidad.event.DisclosureEvent;
import org.apache.lucene.search.BooleanQuery;
import org.springframework.beans.factory.annotation.Autowired;
import uk.ac.ebi.intact.model.*;
import uk.ac.ebi.intact.persistence.svc.SearchService;
import uk.ac.ebi.intact.persistence.svc.impl.SimpleSearchService;
import uk.ac.ebi.intact.services.search.JpaBaseController;
import uk.ac.ebi.intact.services.search.SearchWebappException;
import uk.ac.ebi.intact.services.search.model.SearchResultDataModel;
import uk.ac.ebi.intact.binarysearch.webapp.application.AppConfigBean;
import uk.ac.ebi.intact.binarysearch.webapp.application.OlsBean;
import uk.ac.ebi.intact.binarysearch.webapp.view.search.AdvancedSearch;
import uk.ac.ebi.intact.binarysearch.webapp.view.search.RelatedResults;
import uk.ac.ebi.intact.binarysearch.webapp.view.search.QueryHelper;
import uk.ac.ebi.intact.binarysearch.webapp.util.WebappUtils;
import uk.ac.ebi.intact.binarysearch.webapp.model.TooManyResults;
import uk.ac.ebi.intact.binarysearch.webapp.servlet.ExportServlet;
import uk.ac.ebi.intact.binarysearch.webapp.generated.SearchConfig;

import javax.faces.component.UIParameter;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import java.util.Map;
import java.io.IOException;

/**
 * TODO comment this
 *
 * @author Bruno Aranda (baranda@ebi.ac.uk)
 * @version $Id$
 */
@ViewController(viewIds = {"/search.xhtml","/pages/binarysearch/binarysearch.xhtml"})
public class SearchController extends JpaBaseController {

    private static final Log log = LogFactory.getLog(SearchController.class);

    private static final String ADV_SEARCH_PARAM = "advSearch";
    private static final String QUERY_PARAM = "query";
    private static final String VIEW_PARAM = "view";
    private static final String CLASS_PARAM = "class";
    private static final String MAX_RESULTS_INIT_PARAM = "psidev.MAX_SEARCH_RESULTS";

    // injected
    @Autowired
    private AppConfigBean appConfigBean;

    @Autowired
    private OlsBean olsBean;

    private String searchQuery;
    private String searchClassName;
    private SearchResultDataModel searchResults;

     // vars
    private AdvancedSearch advancedSearch;
    private RelatedResults relatedResults;
    private int pageSize = 30;

    // status flags
    private boolean advancedMode;
    private boolean searchDone;
    private boolean relatedPollEnabled;

    private boolean showProperties;
    private boolean expandedView;

    // bindings
    private UIXTable resultsDataTable;
    private CoreSelectRangeChoiceBar rangeChoiceBar;

    // results
    private uk.ac.ebi.intact.binarysearch.webapp.model.SearchResultDataModel results;

    // export
    private String exportFormat;

    private ResultStats resultStats;


    public SearchController() {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        int maxResults = Integer.parseInt(facesContext.getExternalContext().getInitParameter(MAX_RESULTS_INIT_PARAM));
        BooleanQuery.setMaxClauseCount(maxResults);

        this.advancedSearch = new AdvancedSearch();

        this.resultStats = new ResultStats();
    }

    @PreRenderView
    public void initialParams() {
        FacesContext context = FacesContext.getCurrentInstance();
        String queryParam = context.getExternalContext().getRequestParameterMap().get(QUERY_PARAM);
        String searchClassParam = context.getExternalContext().getRequestParameterMap().get(CLASS_PARAM);

        if (queryParam != null) {
            ConversationUtils.invalidateAndRestartCurrent();
            searchQuery = queryParam;
            searchClassName = searchClassFromParam(searchClassParam);
            doSearch(null);
        }

        if (searchQuery == null) {
            searchQuery = "*";
            doSearch(null);
        }

        String advSearchParam = context.getExternalContext().getRequestParameterMap().get(ADV_SEARCH_PARAM);
        String viewParam = context.getExternalContext().getRequestParameterMap().get(VIEW_PARAM);

        if (advSearchParam != null) {
            this.advancedMode = true;
        }

        if (viewParam != null) {
            if ("exp".equals(viewParam)) {
                expandedView = true;
            }
        }
    }

    public void doSearch(ActionEvent evt) {
        if (searchClassName != null) {
            if (log.isDebugEnabled()) log.debug("Searching query: "+searchQuery+" - class: "+searchClassName);

            Class searchClass = null;
            try {
                searchClass = Class.forName(searchClassName);
            }
            catch (ClassNotFoundException e) {
                throw new SearchWebappException("Searchable class does not exist: " + searchClassName);
            }

            // if the search class is interactor, search with the interactor subtypes excluding the interaction
            this.searchResults = new SearchResultDataModel(new Class[] {searchClass}, searchQuery);

        } else { // count
            if (log.isDebugEnabled()) log.debug("Counting results for query: "+searchQuery);

            SearchService service = new SimpleSearchService();

            Map<Class<? extends Searchable>, Integer> counts =
                    service.count(SearchService.STANDARD_SEARCHABLES, searchQuery);

            final Integer cvObjectCount = counts.containsKey(CvObject.class) ? counts.get(CvObject.class) : 0;
            final Integer proteinCount = counts.containsKey(ProteinImpl.class) ? counts.get(ProteinImpl.class) : 0;
            final Integer smallMolCount = counts.containsKey(SmallMoleculeImpl.class) ? counts.get(SmallMoleculeImpl.class) : 0;
            final Integer nucAcidCount = counts.containsKey(NucleicAcidImpl.class) ? counts.get(NucleicAcidImpl.class) : 0;
            final Integer interactionCount = counts.containsKey(InteractionImpl.class) ? counts.get(InteractionImpl.class) : 0;
            final Integer experimentCount = counts.containsKey(Experiment.class) ? counts.get(Experiment.class) : 0;

            this.resultStats = new ResultStats(
                    cvObjectCount, proteinCount,
                    smallMolCount, nucAcidCount, interactionCount,
                    experimentCount);
        }
    }

    public void doSearchAll(ActionEvent evt) {
        searchClassName = null;
        doSearch(evt);
    }

    public void doSearchClass(ActionEvent evt) {
        UIParameter param = (UIParameter) evt.getComponent().getChildren().get(0);
        searchClassName = (String) param.getValue();
        doSearch(evt);
    }

    private String searchClassFromParam(String searchClassParam) {
        Class<? extends Searchable> searchClass = null;

        if ("experiment".equalsIgnoreCase(searchClassParam)) {
            searchClass = Experiment.class;
        } else if ("interaction".equalsIgnoreCase(searchClassParam)) {
            searchClass = InteractionImpl.class;
        } else if ("interactor".equalsIgnoreCase(searchClassParam)) {
            searchClass = InteractorImpl.class;
        } else if ("cv".equalsIgnoreCase(searchClassParam)) {
            searchClass = CvObject.class;
        } else if ("biosource".equalsIgnoreCase(searchClassParam)) {
            searchClass = BioSource.class;
        }

        if (searchClass == null) {
            return null;
        }

        return searchClass.getName();
    }

    public void doBinarySearch(ActionEvent evt) {
        relatedResults = null;

        // reset the status of the range choice bar
        if (rangeChoiceBar != null) {
            rangeChoiceBar.setFirst(0);
        }

        if (isAdvancedMode()) {
            searchQuery = QueryHelper.createQuery(advancedSearch, olsBean.getInteractionTypeTerms(), olsBean.getDetectionMethodTerms());
        } else {
            searchQuery = QueryHelper.prepareQuery(searchQuery);
        }

        if (log.isDebugEnabled()) log.debug("Searching: " + searchQuery);

        String indexDirectory = WebappUtils.getDefaultIndex(appConfigBean.getConfig()).getLocation();
        try {

            results = new uk.ac.ebi.intact.binarysearch.webapp.model.SearchResultDataModel(searchQuery, indexDirectory, pageSize);

            if (log.isDebugEnabled()) log.debug("\tResults: " + results.getRowCount());
        }
        catch (TooManyResults e) {
            e.printStackTrace();
            //tooManyResults = true;
        }

        resultsDataTable.setFirst(0);

        relatedPollEnabled = true;
        searchDone = true;
    }

    public void doAdvancedSearch(ActionEvent evt) {
        advancedMode = true;
        doSearch(evt);
    }

    public void doCalculateRelatedResults(ActionEvent evt) {
        if (QueryHelper.isLuceneQuery(searchQuery)) {
            if (log.isDebugEnabled()) log.debug("Related results not calculated, cause it is a complex/lucene query");

            relatedResults = null;
            return;
        }
        try {
            if (log.isDebugEnabled()) log.debug("Calculating related results...");

            SimpleSearchService searchService = new SimpleSearchService();
            int numExperiments = searchService.count(Experiment.class, searchQuery);
            int numProteins = searchService.count(ProteinImpl.class, searchQuery);

            this.relatedResults = new RelatedResults();
            relatedResults.setNumOfExperiments(numExperiments);
            relatedResults.setNumOfInteractors(numProteins);

            if (log.isDebugEnabled()) log.debug("\tReturned "+numExperiments+" experiments and "+numProteins+" proteins");

        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void doClearAdvancedForm(ActionEvent evt) {
        this.advancedSearch = new AdvancedSearch();
        this.advancedMode = true;
    }

    public String doExport() {
        // /export?query=#{searchBean.query}&amp;format=mitab&amp;sort=#{searchBean.sortColumn}&amp;asc=#{searchBean.sortAscending}

        // to go to an external URL, we need to shortcircuit the jsf lifecycle
        FacesContext context = FacesContext.getCurrentInstance();

        String exportUrl = context.getExternalContext().getRequestContextPath()+"/export?"+
                           ExportServlet.PARAM_QUERY + "=" + results.getSearchQuery() + "&" +
                           ExportServlet.PARAM_FORMAT + "=" + exportFormat + "&" +
                           ExportServlet.PARAM_SORT + "=" + results.getSortColumn() + "&" +
                           ExportServlet.PARAM_SORT_ASC + "=" + results.isAscending();

        // short-circuit the cycle to redirect to a external page
        try {
            context.responseComplete();
            context.getExternalContext().redirect(exportUrl);
        }
        catch (IOException e) {
            throw new uk.ac.ebi.intact.binarysearch.webapp.SearchWebappException(e);
        }

        return null;
    }

    public void rangeChanged(RangeChangeEvent evt) {
        results.setRowIndex(evt.getNewStart());
        ((UIXTable)resultsDataTable).setFirst(evt.getNewStart());
        //results.fetchResults(evt.getNewStart(), 30);
    }

    public void forceSimpleMode(ActionEvent evt) {
        advancedMode = false;
    }

    public SearchConfig.Indexes.Index getDefaultIndex() {
        return WebappUtils.getDefaultIndex(appConfigBean.getConfig());
    }

    public void doDiscoleRelatedResults(DisclosureEvent evt) {
        searchClassName = null;
        doSearch(null);
    }

    public boolean isAdvancedMode()
    {
        return advancedMode;
    }

    public void setAdvancedMode(boolean advancedMode)
    {
        this.advancedMode = advancedMode;
    }

    public boolean isSearchDone()
    {
        return searchDone;
    }

    public void setSearchDone(boolean searchDone)
    {
        this.searchDone = searchDone;
    }

    public UIXTable getResultsDataTable()
    {
        return resultsDataTable;
    }

    public void setResultsDataTable(UIXTable resultsDataTable)
    {
        this.resultsDataTable = resultsDataTable;
    }

    public uk.ac.ebi.intact.binarysearch.webapp.model.SearchResultDataModel getResults()
    {
        return results;
    }

    public void setResults(uk.ac.ebi.intact.binarysearch.webapp.model.SearchResultDataModel results)
    {
        this.results = results;
    }

    public int getPageSize()
    {
        return pageSize;
    }

    public void setPageSize(int pageSize)
    {
        this.pageSize = pageSize;
    }

    public AdvancedSearch getAdvancedSearch()
    {
        return advancedSearch;
    }

    public void setAdvancedSearch(AdvancedSearch advancedSearch)
    {
        this.advancedSearch = advancedSearch;
    }

    public RelatedResults getRelatedResults()
    {
        return relatedResults;
    }

    public void setRelatedResults(RelatedResults relatedResults)
    {
        this.relatedResults = relatedResults;
    }

    public boolean isShowProperties() {
        return showProperties;
    }

    public void setShowProperties(boolean showProperties) {
        this.showProperties = showProperties;
    }

    public boolean isExpandedView() {
        return expandedView;
    }

    public void setExpandedView(boolean expandedView) {
        this.expandedView = expandedView;
    }

    public String getExportFormat() {
        return exportFormat;
    }

    public void setExportFormat(String exportFormat) {
        this.exportFormat = exportFormat;
    }

    public boolean isRelatedPollEnabled() {
        return relatedPollEnabled;
    }

    public void setRelatedPollEnabled(boolean relatedPollEnabled) {
        this.relatedPollEnabled = relatedPollEnabled;
    }

    public CoreSelectRangeChoiceBar getRangeChoiceBar() {
        return rangeChoiceBar;
    }

    public void setRangeChoiceBar(CoreSelectRangeChoiceBar rangeChoiceBar) {
        this.rangeChoiceBar = rangeChoiceBar;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public String getSearchClassName() {
        return searchClassName;
    }

    public void setSearchClassName(String searchClassName) {
        this.searchClassName = searchClassName;
    }

    public ResultStats getResultStats() {
        return resultStats;
    }

    public SearchResultDataModel getSearchResults() {
        return searchResults;
    }

    public void setSearchResults(SearchResultDataModel searchResults) {
        this.searchResults = searchResults;
    }
}
