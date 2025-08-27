package com.llaama.linthaal.agents.google

import com.llaama.linthaal.agents.google.BioAgentDefaultTest.fewAbstracts
import com.llaama.linthaal.agents.ncbi.eutils.EutilsADT.{PMAbstract, PMAbstractDates}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import org.slf4j.LoggerFactory

import java.util.Date

/** root - info@llaama.com - August 2025
  */

class BioAgentDefaultTest extends AnyFlatSpec with should.Matchers {
  private val log = LoggerFactory.getLogger(getClass.toString)

  import BioPathwaysDefault.*

  "a simple Linthaal Google GenAI prompt " should "return a simple summary " in {

    val genAI = BioPathwaysDefault()

    val response = genAI.extractPathwaysFromAbstracts(fewAbstracts)

    val text = response.text
    log.info(text)

    text should include ("pancreatic")
    text should include ("cancer")
  }
}

object BioAgentDefaultTest {
  val defaultMockDate = PMAbstractDates("2025-08-22")
  val fewAbstracts = Set(
    PMAbstract(10,
      """Minimizing and quantifying uncertainty in AI-informed decisions: Applications in medicine.
        |""".stripMargin,
      """"AI is now a cornerstone of modern dataset analysis. In many real world applications, practitioners are concerned with controlling specific kinds of errors, rather than minimizing the overall number of errors. For example, biomedical screening assays may primarily be concerned with mitigating the number of false positives rather than false negatives. Quantifying uncertainty in AI-based predictions, and in particular those controlling specific kinds of errors, remains theoretically and practically challenging. We develop a strategy called multidimensional informed generalized hypothesis testing (MIGHT) which we prove accurately quantifies uncertainty and confidence given sufficient data, and concomitantly controls for particular error types. Our key insight was that it is possible to integrate canonical cross-validation and parametric calibration procedures within a nonparametric ensemble method. Simulations demonstrate that while typical AI based-approaches cannot be trusted to obtain the truth, MIGHT can be. We apply MIGHT to answer an open question in liquid biopsies using circulating cell-free DNA (ccfDNA) in individuals with or without cancer: Which biomarkers, or combinations thereof, can we trust? Performance estimates produced by MIGHT on ccfDNA data have coefficients of variation that are often orders of magnitude lower than other state of the art algorithms such as support vector machines, random forests, and Transformers, while often also achieving higher sensitivity. We find that combinations of variable sets often decrease rather than increase sensitivity over the optimal single variable set because some variable sets add more noise than signal. This work demonstrates the importance of quantifying uncertainty and confidence-with theoretical guarantees-for the interpretation of real-world data.\"\"\".stripMargin
        |"""".stripMargin, defaultMockDate),
    PMAbstract(11,
      """Identification of Anticancer Target Combinations to Treat Pancreatic Cancer and Its Associated Cachexia Using Constraint-Based Modeling.
        |""".stripMargin,
      """" Pancreatic cancer is frequently accompanied by cancer-associated cachexia, a debilitating metabolic syndrome marked by progressive skeletal muscle wasting and systemic metabolic dysfunction. This study presents a systems biology framework to simultaneously identify therapeutic targets for both pancreatic ductal adenocarcinoma (PDAC) and its associated cachexia (PDAC-CX), using cell-specific genome-scale metabolic models (GSMMs). The human metabolic network Recon3D was extended to include protein synthesis, degradation, and recycling pathways for key inflammatory and structural proteins. These enhancements enabled the reconstruction of cell-specific GSMMs for PDAC and PDAC-CX, and their respective healthy counterparts, based on transcriptomic datasets. Medium-independent metabolic biomarkers were identified through Parsimonious Metabolite Flow Variability Analysis and differential expression analysis across five nutritional conditions. A fuzzy multi-objective optimization framework was employed within the anticancer target discovery platform to evaluate cell viability and metabolic deviation as dual criteria for assessing therapeutic efficacy and potential side effects. While single-enzyme targets were found to be context-specific and medium-dependent, eight combinatorial targets demonstrated robust, medium-independent effects in both PDAC and PDAC-CX cells. These include the knockout of SLC29A2, SGMS1, CRLS1, and the RNF20-RNF40 complex, alongside upregulation of CERK and PIKFYVE. The proposed integrative strategy offers novel therapeutic avenues that address both tumor progression and cancer-associated cachexia, with improved specificity and reduced off-target effects, thereby contributing to translational oncology.
        |"""".stripMargin, defaultMockDate),
    PMAbstract(1,
      """GABRINOX-2 protocol: a French, prospective, multicentre, randomised phase II trial evaluating gemcitabine/nab-paclitaxel followed by FOLFIRINOX versus FOLFIRINOX alone as first-line treatment for metastatic pancreatic cancer.
        |""".stripMargin,
      """"Pancreatic adenocarcinoma is a major public health concern due to its high metastatic potential and poor prognosis. However, treatment options remain limited. A promising therapeutic strategy involves the sequential administration of standard therapies. In a previous phase Ib-II trial, we evaluated a sequential regimen of gemcitabine plus nab-paclitaxel (GEMBRAX) followed by FOLFIRINOX (FFX), which improved median overall survival (OS), progression-free survival and objective response rate (ORR), with acceptable toxicity. This phase II randomised trial will assess the efficacy of GEMBRAX followed by FFX compared with FFX alone as a first-line treatment for metastatic pancreatic cancer (mPC).
        |"""".stripMargin, defaultMockDate),
    PMAbstract(2,
      """The role of formin-like protein 1 in pancreatic cancer and its specific effects on immunity.
        |""".stripMargin,
      """"ObjectiveTo investigate the functions and immunological implication of formin-like protein 1 in pancreatic cancer.MethodsA multitude of public datasets and an in-house cohort were used to assess the clinical relevance and the immunological relevance of formin-like protein 1 in pancreatic cancer. Subsequently, in vitro assays were conducted to evaluate the biological roles of formin-like protein 1 in pancreatic cancer and its effects on immunity.ResultsThe expression of formin-like protein 1 was elevated in pancreatic cancer tissues and linked to a poor prognosis in pancreatic cancer. In vitro assays showed that elevated expression of formin-like protein 1 promoted pancreatic cancer progression. Moreover, formin-like protein 1 was linked to an inflamed tumor microenvironment and mediated epithelial-mesenchymal transition and programmed cell death 1 ligand 1 expression in pancreatic cancer.ConclusionsFormin-like protein 1 is a biomarker of an inflamed tumor microenvironment and positively modulates epithelial-mesenchymal transition and programmed cell death 1 ligand 1 expression in pancreatic cancer, which could be utilized as a novel target for antitumor immunity for more in-depth studies.
        |"""".stripMargin, defaultMockDate),
    PMAbstract(3,
      """Tracing the evolution of single-cell 3D genomes in Kras-driven cancers.
        |""".stripMargin,
      """"Although three-dimensional (3D) genome structures are altered in cancer, it remains unclear how these changes evolve and diversify during cancer progression. Leveraging genome-wide chromatin tracing to visualize 3D genome folding directly in tissues, we generated 3D genome cancer atlases of oncogenic Kras-driven mouse lung adenocarcinoma (LUAD) and pancreatic ductal adenocarcinoma. Here we define nonmonotonic, stage-specific alterations in 3D genome compaction, heterogeneity and compartmentalization as cancers progress from normal to preinvasive and ultimately to invasive tumors, discovering a potential structural bottleneck in early tumor progression. Remarkably, 3D genome architectures distinguish morphologic cancer states in single cells, despite considerable cell-to-cell heterogeneity. Analyses of genome compartmentalization changes not only showed that compartment-associated genes are more homogeneously regulated but also elucidated prognostic and dependency genes in LUAD, as well as an unexpected role for Rnf2 in 3D genome regulation. Our results highlight the power of single-cell 3D genome mapping to identify diagnostic, prognostic and therapeutic biomarkers in cancer.
        |"""".stripMargin, defaultMockDate),
    PMAbstract(4,
      """ Fragmentation signatures in cancer patients resemble those of patients with vascular or autoimmune diseases.
        |""".stripMargin,
      """"Multiple case-controlled studies have shown that analyzing fragmentation patterns in plasma cell-free DNA (cfDNA) can distinguish individuals with cancer from healthy controls. However, there have been few studies that investigate various types of cfDNA fragmentomics patterns in individuals with other diseases. We therefore developed a comprehensive statistic, called fragmentation signatures, that integrates the distributions of fragment positioning, fragment length, and fragment end-motifs in cfDNA. We found that individuals with venous thromboembolism, systemic lupus erythematosus, dermatomyositis, or scleroderma have cfDNA fragmentation signatures that closely resemble those found in individuals with advanced cancers. Furthermore, these signatures were highly correlated with increases in inflammatory markers in the blood. We demonstrate that these similarities in fragmentation signatures lead to high rates of false positives in individuals with autoimmune or vascular disease when evaluated using conventional binary classification approaches for multicancer earlier detection (MCED). To address this issue, we introduced a multiclass approach for MCED that integrates fragmentation signatures with protein biomarkers and achieves improved specificity in individuals with autoimmune or vascular disease while maintaining high sensitivity. Though these data put substantial limitations on the specificity of fragmentomics-based tests for cancer diagnostics, they also offer ways to improve the interpretability of such tests. Moreover, we expect these results will lead to a better understanding of the process-most likely inflammatory-from which abnormal fragmentation signatures are derived.
        |"""".stripMargin, defaultMockDate),
    PMAbstract(5,
      """ Clinical outcomes of gastroenteropancreatic neuroendocrine neoplasms in Taiwan: A multicenter registry study-TCOG T1214 study.
        |""".stripMargin,
      """"Gastroenteropancreatic neuroendocrine neoplasms (GEP-NENs) account for more than 50% of all NENs. The survival of patients with GEP-NENs has improved based on early diagnosis and improved treatment strategies. The real-world data of GEP-NENs in Taiwan are limited. A multicenter registry study was conducted to obtain real-world data on GEP-NENs in Taiwan.
        |Patients with pathologically diagnosed GEP-NENs were enrolled. Data were on the baseline characteristics, treatment strategies, and patient survival. Also evaluated was the expression status of six biomarkers, including SSTR2, SSTR5, PDX-1, CDX-2, mASH1, and NeuroD, in tumors. Overall survival (OS) was analyzed and plotted via the Kaplan-Meier method. Cox regression analysis was used to analyze the prognostic factors of OS.
        |A total of 600 GEP-NEN patients were enrolled. Pancreatic NENs accounted for 43.0% of all patients. The 5-year and 10-year OS rates of all patients were 70.9% and 61.3%, respectively. In the multivariable Cox regression analysis, older age (hazard ratio [HR] = 1.02; 95% CI, 1.01-1.03), higher Eastern Cooperative Oncology Group performance status score, higher tumor grade (World Health Organization classification) and stage 4 disease (HR = 6.22; 95% CI, 3.60-10.76) were associated with poor OS. Positive SSTR2 expression (HR = 0.53; 95% CI, 0.31-0.91) was associated with better OS according to multivariate Cox regression analysis.
        |CONCLUSIONS: This study provides real-world data on 600 GEP-NENs in Taiwan and identifies age, Eastern Cooperative Oncology Group performance status score, tumor grade, tumor stage, and SSTR2 expression as prognostic factors for the survival of GEP-NENs.
        |"""".stripMargin, defaultMockDate),
  )
}