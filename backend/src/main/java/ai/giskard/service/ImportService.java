package ai.giskard.service;

import ai.giskard.domain.*;
import ai.giskard.domain.ml.Dataset;
import ai.giskard.domain.ml.FunctionInput;
import ai.giskard.domain.ml.ProjectModel;
import ai.giskard.domain.ml.TestSuite;
import ai.giskard.repository.FeedbackRepository;
import ai.giskard.repository.ProjectRepository;
import ai.giskard.repository.UserRepository;
import ai.giskard.repository.ml.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final FeedbackRepository feedbackRepository;
    private final DatasetRepository datasetRepository;
    private final ModelRepository modelRepository;
    private final TestFunctionRepository testFunctionRepository;
    private final SlicingFunctionRepository slicingFunctionRepository;
    private final TransformationFunctionRepository transformationFunctionRepository;
    private final UserRepository userRepository;
    private final FileLocationService locationService;
    private final TestSuiteService testSuiteService;
    private final ProjectRepository projectRepository;
    private final TestSuiteRepository testSuiteRepository;


    private Map<UUID, UUID> saveImportDataset(List<Dataset> datasets, Project savedProject) {
        Map<UUID, UUID> mapFormerNewIdDataset = new HashMap<>();
        datasets.forEach(dataset -> {
            UUID formerId = dataset.getId();
            UUID newId = UUID.randomUUID();
            dataset.setProject(savedProject);
            dataset.setId(newId);
            mapFormerNewIdDataset.put(formerId, newId);
            datasetRepository.save(dataset);
        });
        return mapFormerNewIdDataset;
    }

    private Map<UUID, UUID> saveImportModel(List<ProjectModel> models, Project savedProject) {
        Map<UUID, UUID> mapFormerNewIdModel = new HashMap<>();
        models.forEach(model -> {
            UUID formerId = model.getId();
            UUID newId = UUID.randomUUID();
            model.setProject(savedProject);
            model.setId(newId);
            mapFormerNewIdModel.put(formerId, newId);
            modelRepository.save(model);
        });
        return mapFormerNewIdModel;
    }

    private void saveImportFeedback(List<Feedback> feedbacks, Project savedProject, Map<UUID, UUID> mapFormerNewIdModel, Map<UUID, UUID> mapFormerNewIdDataset, Map<String, String> importedUsersToCurrent) {
        feedbacks.forEach(feedback -> {
            feedback.setProject(savedProject);
            feedback.setDataset(datasetRepository.getMandatoryById(mapFormerNewIdDataset.get(feedback.getDataset().getId())));
            feedback.setModel(modelRepository.getMandatoryById(mapFormerNewIdModel.get(feedback.getModel().getId())));
            feedback.setUser(userRepository.getOneByLogin(importedUsersToCurrent.get(feedback.getUser().getLogin())));
            feedback.getFeedbackReplies().forEach(reply -> {
                reply.setFeedback(feedback);
                reply.setUser(userRepository.getOneByLogin(importedUsersToCurrent.get(reply.getUser().getLogin())));
            });
            feedbackRepository.save(feedback);
        });
    }

    private void saveImportTestSuites(List<TestSuite> testSuites, Project savedProject,
                                      Map<UUID, UUID> mapFormerNewIdModel,
                                      Map<UUID, UUID> mapFormerNewIdDataset) {
        testSuites.forEach(suite -> {
            suite.setProject(savedProject);

            suite.getTests().forEach(test -> {
                test.setSuite(suite);
                test.setTestFunction(testFunctionRepository.getById(test.getTestFunction().getUuid()));
                test.getExecutions().forEach(suiteTestExecution -> suiteTestExecution.setTest(test));
            });

            suite.getExecutions().forEach(execution -> {
                execution.setSuite(suite);
                execution.getResults().forEach(suiteTestExecution -> suiteTestExecution.setExecution(execution));
            });

            Stream.of(
                    suite.getTests().stream()
                        .flatMap(suiteTest -> suiteTest.getFunctionInputs().stream()),
                    suite.getFunctionInputs().stream(),
                    suite.getExecutions().stream()
                        .flatMap(testSuiteExecution -> testSuiteExecution.getInputs().stream())
                )
                .reduce(Stream::concat)
                .orElseGet(Stream::empty)
                .filter(functionInput -> !functionInput.isAlias())
                .forEach(functionInput -> replaceFunctionInputValues(mapFormerNewIdModel, mapFormerNewIdDataset, functionInput));

            testSuiteRepository.save(suite);
        });
    }

    private static void replaceFunctionInputValues(Map<UUID, UUID> mapFormerNewIdModel, Map<UUID, UUID> mapFormerNewIdDataset, FunctionInput functionInput) {
        if (Objects.equals(functionInput.getType(), "BaseModel")) {
            functionInput.setValue(mapFormerNewIdModel.get(UUID.fromString(functionInput.getValue())).toString());
        } else if (Objects.equals(functionInput.getType(), "Dataset")) {
            functionInput.setValue(mapFormerNewIdDataset.get(UUID.fromString(functionInput.getValue())).toString());
        }
    }

    private Project saveImportProject(Project project, String userNameOwner, String projectKey, Map<String, String> importedUsersToCurrent) {
        project.setOwner(userRepository.getOneByLogin(userNameOwner));
        project.setKey(projectKey);
        Set<User> guestList = new HashSet<>();
        importedUsersToCurrent.forEach((key, value) -> {
            if (!value.equals(userNameOwner))
                guestList.add(userRepository.getOneByLogin(value));
        });
        project.setGuests(guestList);
        return projectRepository.save(project);
    }

    /**
     * Copies a folder inside the temporary directory provided into the project's directory,
     * then iterates over subfolders
     * to rename them according to the provided idMap
     */
    private void copyFilesToProjectFolder(Project newProject, Path metadataTmpDirectory, Map<UUID, UUID> idMap, String folderName) throws IOException {
        Path projectDir = locationService.resolvedProjectHome(newProject.getKey());
        Path tmpModelDir = metadataTmpDirectory.resolve(folderName);
        Path finalModelDir = projectDir.resolve(folderName);

        FileUtils.copyDirectory(tmpModelDir.toFile(), finalModelDir.toFile());

        for (Map.Entry<UUID, UUID> ids : idMap.entrySet()) {
            Files.move(finalModelDir.resolve(ids.getKey().toString()), finalModelDir.resolve(ids.getValue().toString()));
        }
    }

    private void copyFilesToGlobalFolder(Path metadataTmpDirectory, Set<UUID> ids, String folderName) throws IOException {
        Path sourceDir = metadataTmpDirectory.resolve("global").resolve(folderName);
        Path targetDir = locationService.globalPath().resolve(folderName);

        for (UUID id : ids) {
            if (!Files.exists(targetDir.resolve(id.toString()))) {
                Files.move(sourceDir.resolve(id.toString()), targetDir.resolve(id.toString()));
            }
        }
    }

    Project importProject(Map<String, String> importedUsersToCurrent, String metadataDirectory, String projectKey, String userNameOwner) throws IOException {
        Path pathMetadataDirectory = Paths.get(metadataDirectory);
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Get back object from file
        Project project = mapper.readValue(locationService.resolvedMetadataPath(pathMetadataDirectory, Project.class.getSimpleName()).toFile(), new TypeReference<>() {
        });
        List<ProjectModel> models = mapper.readValue(locationService.resolvedMetadataPath(pathMetadataDirectory, ProjectModel.class.getSimpleName()).toFile(), new TypeReference<>() {
        });
        List<Dataset> datasets = mapper.readValue(locationService.resolvedMetadataPath(pathMetadataDirectory, Dataset.class.getSimpleName()).toFile(), new TypeReference<>() {
        });
        List<TestFunction> testFunctions = mapper.readValue(locationService.resolvedMetadataPath(pathMetadataDirectory, TestFunction.class.getSimpleName()).toFile(), new TypeReference<>() {
        });
        List<SlicingFunction> slicingFunctions = mapper.readValue(locationService.resolvedMetadataPath(pathMetadataDirectory, SlicingFunction.class.getSimpleName()).toFile(), new TypeReference<>() {
        });
        List<TransformationFunction> transformationFunctions = mapper.readValue(locationService.resolvedMetadataPath(pathMetadataDirectory, TransformationFunction.class.getSimpleName()).toFile(), new TypeReference<>() {
        });
        List<Feedback> feedbacks = mapper.readValue(locationService.resolvedMetadataPath(pathMetadataDirectory, Feedback.class.getSimpleName()).toFile(), new TypeReference<>() {
        });
        List<TestSuite> testSuites = mapper.readValue(testSuiteService.resolvedMetadataPath(pathMetadataDirectory, TestSuite.class.getSimpleName()).toFile(), new TypeReference<>() {
        });
        Project savedProject = saveImportProject(project, userNameOwner, projectKey, importedUsersToCurrent);

        // Save new objects in memory
        Map<UUID, UUID> mapFormerNewIdModel = saveImportModel(models, savedProject);
        Map<UUID, UUID> mapFormerNewIdDataset = saveImportDataset(datasets, savedProject);

        testFunctionRepository.saveAllIfNotExists(testFunctions);
        slicingFunctionRepository.saveAllIfNotExists(slicingFunctions);
        transformationFunctionRepository.saveAllIfNotExists(transformationFunctions);

        saveImportFeedback(feedbacks, savedProject, mapFormerNewIdModel, mapFormerNewIdDataset, importedUsersToCurrent);
        saveImportTestSuites(testSuites, savedProject, mapFormerNewIdModel, mapFormerNewIdDataset);

        // Once everything is remapped, at this stage we want to save the files into appropriate folders
        copyFilesToProjectFolder(savedProject, pathMetadataDirectory, mapFormerNewIdModel, "models");
        copyFilesToProjectFolder(savedProject, pathMetadataDirectory, mapFormerNewIdDataset, "datasets");

        copyFilesToGlobalFolder(pathMetadataDirectory, findTests(project), "tests");
        copyFilesToGlobalFolder(pathMetadataDirectory, findReferencedEntities(project, "SlicingFunction"), "slices");
        copyFilesToGlobalFolder(pathMetadataDirectory, findReferencedEntities(project, "TransformationFunction"), "transformations");

        return projectRepository.save(savedProject);
    }

    private Set<UUID> findTests(Project project) {
        return project.getTestSuites().stream()
            .flatMap(testSuite -> testSuite.getTests().stream())
            .map(suiteTest -> suiteTest.getTestFunction().getUuid())
            .collect(Collectors.toSet());
    }

    private Set<UUID> findReferencedEntities(Project project, String type) {
        return project.getTestSuites().stream()
            .flatMap(testSuite -> Stream.concat(
                testSuite.getTests().stream()
                    .flatMap(suiteTest -> suiteTest.getFunctionInputs().stream()),
                testSuite.getFunctionInputs().stream()
            ).filter(functionInput -> !functionInput.isAlias() && type.equals(functionInput.getType())))
            .map(functionInput -> UUID.fromString(functionInput.getValue()))
            .collect(Collectors.toSet());
    }

}
