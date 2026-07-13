package com.shrimpfarm.app.qa.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Answer {
    public long id;
    @SerializedName("question_id") public long questionId;
    @SerializedName("user_id") public String userId;
    public String content;
    @SerializedName("is_accepted") public boolean isAccepted;
    @SerializedName("image_urls") public List<String> imageUrls;
    @SerializedName("created_at") public String createdAt;
    @SerializedName("updated_at") public String updatedAt;

    public transient String displayName;
    public transient int upvotes;
    public transient int downvotes;
    public transient int userVote;
}
