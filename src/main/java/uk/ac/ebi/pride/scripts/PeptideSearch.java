package uk.ac.ebi.pride.scripts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvBeanReader;
import org.supercsv.io.CsvListWriter;
import org.supercsv.io.ICsvBeanReader;
import org.supercsv.io.ICsvListWriter;
import org.supercsv.prefs.CsvPreference;
import uk.ac.ebi.pride.archive.dataprovider.identification.ModificationProvider;
import uk.ac.ebi.pride.archive.dataprovider.param.CvParamProvider;
import uk.ac.ebi.pride.archive.repo.assay.service.AssayService;
import uk.ac.ebi.pride.archive.repo.assay.service.AssaySummary;
import uk.ac.ebi.pride.archive.repo.param.service.CvParamSummary;
import uk.ac.ebi.pride.archive.repo.project.service.ProjectService;
import uk.ac.ebi.pride.archive.repo.project.service.ProjectSummary;
import uk.ac.ebi.pride.archive.search.service.ProjectSearchService;
import uk.ac.ebi.pride.archive.search.service.ProjectSearchSummary;
import uk.ac.ebi.pride.archive.search.util.SearchFields;
import uk.ac.ebi.pride.archive.security.psm.PsmSecureSearchService;
import uk.ac.ebi.pride.indexutils.helpers.CvParamHelper;
import uk.ac.ebi.pride.indexutils.helpers.ModificationHelper;
import uk.ac.ebi.pride.psmindex.search.model.Psm;
import uk.ac.ebi.pride.psmindex.search.service.PsmSearchService;

import javax.annotation.Resource;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;


/**
 * User: ntoro
 * Date: 08/05/2014
 * Time: 10:34
 */
@Component
public class PeptideSearch {

    private static Logger logger = LoggerFactory.getLogger(PeptideSearch.class);

    private static final int PAGE_SIZE = 20;

    @Resource
    private PsmSecureSearchService psmSecureSearchService;

    @Resource
    private PsmSearchService psmInsecureSearchService;

    @Resource
    private ProjectSearchService projectSearchService;

    @Resource
    private ProjectService projectService;

    @Resource
    private AssayService assayService;


    public static void main(String[] args) {

        ApplicationContext context = new ClassPathXmlApplicationContext("spring/app-context.xml");
        PeptideSearch peptideSearch = context.getBean(PeptideSearch.class);

        if (args.length > 3 && args.length < 2) {
            System.out.print("PeptideSearch species output_file_prefix (input_file)");
            return;
        }

        String taxid = args[0];
        String output_file = args[1];

        if (args.length == 3){
            System.out.printf("PeptideSearch: %s %s %s\n", args[0], args[1], args[2]);

            String input_file = args[2];
            searchPeptideEvidences(peptideSearch, input_file, output_file, taxid);
        }
        else {
            System.out.printf("PeptideSearch: %s %s\n", args[0], args[1]);
            searchPeptideEvidences(peptideSearch, output_file, taxid);
        }

    }


    public static void searchPeptideEvidences(PeptideSearch peptideSearch, String outputPath, String taxid) {

        String queryFields = SearchFields.SPECIES_ACCESSIONS.getIndexName();
        String[] queryFilters = {
                SearchFields.SPECIES_ACCESSIONS.getIndexName() + ":" + taxid,
                SearchFields.SUBMISSION_TYPE.getIndexName()+":PRIDE OR COMPLETE"};
        String order = SearchFields.ACCESSION.getIndexName();
        ICsvListWriter listWriter = null;


        try {
            //Retrieve all public project with this taxId
            long totalResults = peptideSearch.projectSearchService.numSearchResults(taxid, queryFields, queryFilters);
            logger.info("Founds " + totalResults + " Human projects");

            int count = 0;
            int pageNumber;

            while (count < totalResults) {

                pageNumber = count / PAGE_SIZE;
                logger.info("Page number " + pageNumber);

                //We are going to generate a file every ten projects+-
                listWriter = new CsvListWriter(new FileWriter(String.format("%s_%02d.txt", outputPath,pageNumber)), CsvPreference.TAB_PREFERENCE);

                // the header elements are used to map the values to the bean (names must match)
                final String[] outputHeader = new String[]{"found_sequence", "found_protein",
                        "project_accession", "assay_accession", "reported_id", "modifications", "search_engine", "search_engine_score"};

                // write the header
                listWriter.writeHeader(outputHeader);

                final Collection<ProjectSearchSummary> projectSearchSummaries =
                        peptideSearch.projectSearchService.searchProjects(taxid, queryFields, queryFilters, count, PAGE_SIZE, order, "asc");
                count += projectSearchSummaries.size();

                for (ProjectSearchSummary projectSearchSummary : projectSearchSummaries) {

                   //Iterate in every assay to retrieve the psms
                    if(projectSearchSummary != null && projectSearchSummary.getAssayAccessions() != null){

                        for (String assayAc : projectSearchSummary.getAssayAccessions()) {
                            if(assayAc != null){
                                //To be a bit more accurate we check the taxId at assay level again
                                AssaySummary assaySummary = peptideSearch.assayService.findByAccession(assayAc);
                                if (assaySummary != null) {
                                    Collection<CvParamSummary> speciesCollection = assaySummary.getSpecies();
                                    for (CvParamSummary species : speciesCollection) {
                                        if (species.getAccession().equalsIgnoreCase(taxid)) {
                                            List<Psm> psms = peptideSearch.psmSecureSearchService.findByAssayAccession(assayAc);
                                            if (!psms.isEmpty()) {
                                                report(psms, listWriter);
                                            }
                                        } else {
                                            logger.info("No human assay " + assayAc);

                                        }
                                    }
                                }
                            }
                        }
                    }

                }

                //We close the writer before we create the new one;

                try {
                    listWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (listWriter != null) {
                try {
                    listWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void searchPeptideEvidences(PeptideSearch peptideSearch, String inputPath, String outputPath, String taxid) {

        ICsvBeanReader beanReader = null;
        ICsvListWriter listWriter = null;

        try {
            beanReader = new CsvBeanReader(new FileReader(inputPath), CsvPreference.TAB_PREFERENCE);
            listWriter = new CsvListWriter(new FileWriter(outputPath), CsvPreference.TAB_PREFERENCE);

            // the header elements are used to map the values to the bean (names must match)
            final String[] inputHeader = beanReader.getHeader(true);
            final String[] outputHeader = new String[]{"original_sequence", "found_sequence", "original_protein", "found_protein",
                    "project_accession", "assay_accession", "reported_id", "modifications", "search_engine", "search_engine_score"};

            CellProcessor[] inputProcessors = new CellProcessor[]{
                    new NotNull(), // proteins
                    new NotNull(), // sequences
            };

            // write the header
            listWriter.writeHeader(outputHeader);

            InputPeptideSearch pepProt;
            while ((pepProt = beanReader.read(InputPeptideSearch.class, inputHeader, inputProcessors)) != null) {

                logger.info("Reading Line: " + beanReader.getLineNumber());
                List<Psm> aux = new ArrayList<Psm>();

                String originalProtein = pepProt.getProteinName();
                String originalSequence = pepProt.getSequence();


                //Exact match
                List<Psm> psms = peptideSearch.psmInsecureSearchService.findByPeptideSequence(originalSequence);
                logger.debug("Found " + psms.size() + " with exact match");

                //Subsequence match
                List<Psm> submatch = peptideSearch.psmInsecureSearchService.findByPeptideSubSequence(originalSequence);
                logger.debug("Found " + submatch.size() + " as a subsequence of the peptide");

                logger.info("Before filtering we found " + (psms.size() + submatch.size()));
                //Filter by species
                for (Psm psm : psms) {
                    ProjectSummary projectSummary = peptideSearch.projectService.findByAccession(psm.getProjectAccession());
                    if (projectSummary.isPublicProject()) {
                        AssaySummary assaySummary = peptideSearch.assayService.findByAccession(psm.getAssayAccession());
                        if (assaySummary != null) {
                            Collection<CvParamSummary> speciesCollection = assaySummary.getSpecies();
                            for (CvParamSummary species : speciesCollection) {
                                if (species.getAccession().equalsIgnoreCase(taxid)) {
                                    aux.add(psm);
                                }
                            }
                        }
                    } else {
                        logger.debug("Private project found " + psm.getProjectAccession());
                    }

                }

                for (Psm psm : submatch) {
                    ProjectSummary projectSummary = peptideSearch.projectService.findByAccession(psm.getProjectAccession());
                    if (projectSummary.isPublicProject()) {
                        AssaySummary assaySummary = peptideSearch.assayService.findByAccession(psm.getAssayAccession());
                        if (assaySummary != null) {
                            Collection<CvParamSummary> speciesCollection = assaySummary.getSpecies();
                            for (CvParamSummary species : speciesCollection) {
                                if (species.getAccession().equalsIgnoreCase(taxid)) {
                                    aux.add(psm);
                                }
                            }
                        }
                    } else {
                        logger.debug("Private project found " + psm.getProjectAccession());
                    }
                }

                logger.info("After filtering we have " + aux.size());
                if (!aux.isEmpty()) {
                    report(originalSequence, originalProtein, aux, listWriter);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (beanReader != null) {
                try {
                    beanReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (listWriter != null) {
                try {
                    listWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private static void report(String originalSequence, String originalProtein, List<Psm> psms, ICsvListWriter listWriter) throws IOException {
        for (Psm psm : psms) {
            listWriter.write(
                    originalSequence,
                    psm.getPeptideSequence(),
                    originalProtein,
                    psm.getProteinAccession(),
                    psm.getProjectAccession(),
                    psm.getAssayAccession(),
                    psm.getReportedId(),
                    printModifications(psm.getModifications()),
                    printCvParams(psm.getSearchEngines()),
                    printCvParams(psm.getSearchEngineScores())
            );
        }
    }

    private static void report(List<Psm> psms, ICsvListWriter listWriter) throws IOException {
        for (Psm psm : psms) {
            listWriter.write(
                    psm.getPeptideSequence(),
                    psm.getProteinAccession(),
                    psm.getProjectAccession(),
                    psm.getAssayAccession(),
                    psm.getReportedId(),
                    printModifications(psm.getModifications()),
                    printCvParams(psm.getSearchEngines()),
                    printCvParams(psm.getSearchEngineScores())
            );
        }
    }
    private static String printModifications(Iterable<ModificationProvider> modifications){

        StringBuilder res = new StringBuilder();
        Iterator<ModificationProvider> iterator = modifications.iterator();
        ModificationProvider modification;

        if(iterator.hasNext()){
            modification = iterator.next();
            res.append(ModificationHelper.convertToString(modification));
        }

        while (iterator.hasNext()) {
            modification = iterator.next();
            res.append(",");
            res.append(ModificationHelper.convertToString(modification));
        }

        return res.toString();

    }

    private static String printCvParams(Iterable<CvParamProvider> cvParams){

        StringBuilder res = new StringBuilder();
        Iterator<CvParamProvider> iterator = cvParams.iterator();
        CvParamProvider cvParam;

        if(iterator.hasNext()){
            cvParam = iterator.next();
            res.append(CvParamHelper.convertToString(cvParam));
        }

        while (iterator.hasNext()) {
            cvParam = iterator.next();
            res.append("|");
            res.append(CvParamHelper.convertToString(cvParam));
        }

        return res.toString();

    }


}
