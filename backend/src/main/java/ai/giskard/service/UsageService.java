package ai.giskard.service;

import ai.giskard.repository.FeedbackRepository;
import ai.giskard.web.dto.PrepareDeleteDTO;
import ai.giskard.web.dto.mapper.GiskardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsageService {
    private final FeedbackRepository feedbackRepository;
    private final GiskardMapper giskardMapper;

    public PrepareDeleteDTO prepareDeleteDataset(UUID datasetId) {
        PrepareDeleteDTO res = new PrepareDeleteDTO();
        res.setFeedbacks(giskardMapper.toLightFeedbacks(feedbackRepository.findAllByDatasetId(datasetId)));
        // TODO: add suite test newt
        return res;
    }

    public PrepareDeleteDTO prepareDeleteModel(UUID modelId) {
        PrepareDeleteDTO res = new PrepareDeleteDTO();
        res.setFeedbacks(giskardMapper.toLightFeedbacks(feedbackRepository.findAllByModelId(modelId)));
        // TODO: add suite test newt
        return res;
    }
}
