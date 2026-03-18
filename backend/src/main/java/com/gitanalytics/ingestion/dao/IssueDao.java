package com.gitanalytics.ingestion.dao;

import com.gitanalytics.ingestion.entity.Issue;

public interface IssueDao {
    void upsert(Issue issue);
}
