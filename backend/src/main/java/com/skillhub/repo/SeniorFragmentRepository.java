package com.skillhub.repo;

import com.skillhub.model.SeniorFragment;

import java.util.List;

public interface SeniorFragmentRepository {
    SeniorFragment save(SeniorFragment f);
    List<SeniorFragment> listBySenior(String seniorId, int limit);
    /** 全库检索：按 senior_id 过滤后返回全部片段，供内存打分（简化 RAG）。 */
    List<SeniorFragment> listAll(String seniorId);
}