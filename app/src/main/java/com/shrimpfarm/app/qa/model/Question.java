package com.shrimpfarm.app.qa.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Question {
    public long id;
    @SerializedName("user_id") public String userId;
    public String title;
    public String content;
    @SerializedName("is_resolved") public boolean isResolved;
    @SerializedName("image_urls") public List<String> imageUrls;
    @SerializedName("created_at") public String createdAt;
    @SerializedName("updated_at") public String updatedAt;
    public List<Answer> answers;

    public transient String displayName;
    public transient int answerCount;
    public transient String latestAnswerAt;
}
