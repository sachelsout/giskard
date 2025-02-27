package ai.giskard.web.rest.controllers;

import ai.giskard.repository.ml.SlicingFunctionRepository;
import ai.giskard.service.SlicingFunctionService;
import ai.giskard.web.dto.ComparisonClauseDTO;
import ai.giskard.web.dto.SlicingFunctionDTO;
import ai.giskard.web.dto.mapper.GiskardMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/")
public class SlicingFunctionController {

    private final GiskardMapper giskardMapper;
    private final SlicingFunctionRepository slicingFunctionRepository;
    private final SlicingFunctionService slicingFunctionService;

    @GetMapping({"project/{projectKey}/slices/{uuid}", "slices/{uuid}"})
    @Transactional(readOnly = true)
    public SlicingFunctionDTO getSlicingFunction(
        @PathVariable(value = "projectKey", required = false) String projectKey,
        @PathVariable("uuid") @NotNull UUID uuid) {
        // TODO GSK-1280: add projectKey to slicing function
        return giskardMapper.toDTO(slicingFunctionRepository.getMandatoryById(uuid));
    }

    @PutMapping({"project/{projectKey}/slices/{uuid}", "slices/{uuid}"})
    public SlicingFunctionDTO createSlicingFunction(@PathVariable(value = "projectKey", required = false) String projectKey,
                                                    @PathVariable("uuid") @NotNull UUID uuid,
                                                    @Valid @RequestBody SlicingFunctionDTO slicingFunction) {
        slicingFunction.setProjectKeys(Set.of(projectKey));
        return slicingFunctionService.save(slicingFunction);
    }

    @PostMapping("project/{projectKey}/slices/no-code")
    public SlicingFunctionDTO createNoCodeSlicingFunction(@PathVariable(value = "projectKey") @NotNull String projectKey,
                                                    @Valid @RequestBody List<@NotNull ComparisonClauseDTO> comparisonClauses) throws JsonProcessingException {
        return slicingFunctionService.generate(comparisonClauses, projectKey);
    }


}
