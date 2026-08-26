	package com.reality.services.impl;
	
	import java.util.List;
	
	import org.springframework.stereotype.Service;
	
	import com.reality.dto.ActivityRequestDTO;
	import com.reality.dto.ActivityResponseDTO;
	import com.reality.entity.Activity;
	import com.reality.exception.InvalidActivityException;
	import com.reality.exception.ResourceNotFoundException;
	import com.reality.repository.ActivityRepository;
	import com.reality.services.ActivityService;
	
	@Service
	public class ActivityServiceImpl implements ActivityService {
	
	    private final ActivityRepository activityRepository;
	    
	    private ActivityResponseDTO mapToResponseDTO(Activity activity) {
	
	        ActivityResponseDTO response = new ActivityResponseDTO();
	
	        response.setId(activity.getId());
	        response.setName(activity.getName());
	        response.setMinimumDuration(activity.getMinimumDuration());
	        response.setCategory(activity.getCategory());
	        response.setActive(activity.getActive());
	        response.setCreatedAt(activity.getCreatedAt());
	        response.setUpdatedAt(activity.getUpdatedAt());
	
	        return response;
	    }
	
	    public ActivityServiceImpl(ActivityRepository activityRepository) {
	        this.activityRepository = activityRepository;
	    }
	
	    @Override
	    public ActivityResponseDTO createActivity(ActivityRequestDTO request) {
	
	        // Business Validation
	    	if (request.getMinimumDuration() == null
			        || request.getMinimumDuration() <= 0) {
	
			    throw new InvalidActivityException(
			            "Minimum duration must be greater than zero.");
			}
	
	        // DTO -> Entity
	        Activity activity = new Activity();
	        activity.setName(request.getName());
	        
	        
	        
	        activity.setMinimumDuration(request.getMinimumDuration());
	        activity.setCategory(request.getCategory());
	
	        // Save into Database
	        Activity savedActivity = activityRepository.save(activity);
	
	        // Entity -> ResponseDTO
	        ActivityResponseDTO response = mapToResponseDTO(savedActivity);
	        
	        return response;
	      
	    }
	
	    
	
	    	@Override
	    	public List<ActivityResponseDTO> getAllActivities() {

	    	    List<Activity> activities = activityRepository.findByActiveTrue();

	    	    return activities.stream()
	    	            .map(this::mapToResponseDTO)
	    	            .toList();
	    	}
	    
	
	    @Override
	    public ActivityResponseDTO getActivityById(Long id) {
	
	        Activity activity = activityRepository.findByIdAndActiveTrue(id)
	                .orElseThrow(() ->
	                        new ResourceNotFoundException("Activity not found with id : " + id));
	
	        return mapToResponseDTO(activity);
	    }
	
	    @Override
	    public ActivityResponseDTO updateActivity(Long id, ActivityRequestDTO request) {
	    	
	    	
	
	        Activity activity = activityRepository.findByIdAndActiveTrue(id)
	                .orElseThrow(() ->
	                        new ResourceNotFoundException("Activity not found with id : " + id));
	        
	        if (request.getMinimumDuration() == null
			        || request.getMinimumDuration() <= 0) {
	
			    throw new InvalidActivityException(
			            "Minimum duration must be greater than zero.");
			}
	
	        activity.setName(request.getName());
	        activity.setMinimumDuration(request.getMinimumDuration());
	        activity.setCategory(request.getCategory());
	
	        Activity updatedActivity = activityRepository.save(activity);
	
	        return mapToResponseDTO(updatedActivity);
	    }
	
	    @Override
	    public void deleteActivity(Long id) {
	
	        Activity activity = activityRepository.findByIdAndActiveTrue(id)
	                .orElseThrow(() ->
	                        new ResourceNotFoundException("Activity not found with id : " + id));
	
	        activity.setActive(false);
	
	        activityRepository.save(activity);
	    }
	}