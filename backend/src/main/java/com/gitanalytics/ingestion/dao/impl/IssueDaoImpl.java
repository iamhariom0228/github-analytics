package com.gitanalytics.ingestion.dao.impl;

import com.gitanalytics.ingestion.dao.IssueDao;
import com.gitanalytics.ingestion.entity.Issue;
import com.gitanalytics.ingestion.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class IssueDaoImpl implements IssueDao {
    private final IssueRepository issueRepository;

    @Override
    public void upsert(Issue issue) {
        issueRepository.upsert(issue);
    }
}
