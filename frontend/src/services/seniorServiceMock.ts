import type { SeniorSkill } from '../types/index';

/**
 * 临时 mock（v1 上线后应删）。当前 seniorService 仍走老 /api/seniors 路径。
 */
export const seniorStoreMock = {
  list: async (): Promise<SeniorSkill[]> => {
    return [
      { id: 'zhang-jingsai', name: '张学长 · 竞赛组', school: '山西大学', major: '自动化与软件学院 · 软件工程', year: '2023', domain: '竞赛', avatarFilename: 'zhang.svg', source: 'manual', createdAt: '2026-08-12T00:00:00Z' },
      { id: 'li-keyan', name: '李学长 · 科研组', school: '山西大学', major: '数学科学学院 · 统计学', year: '2022', domain: '科研', avatarFilename: 'li.svg', source: 'manual', createdAt: '2026-08-12T00:00:00Z' },
      { id: 'chen-baoyan', name: '陈学姐 · 保研组', school: '山西大学', major: '计算机与信息技术学院 · 软件工程', year: '2024', domain: '保研', avatarFilename: 'chen.svg', source: 'manual', createdAt: '2026-08-12T00:00:00Z' },
      { id: 'lin-course-selection', name: '林学长 · 计算机选课避坑', school: '示例大学', major: '计算机学院 · 软件工程', year: '2025', domain: '选课', avatarFilename: 'avatar.svg', source: 'distilled', createdAt: '2026-08-12T00:00:00Z' },
    ];
  },
};

export default seniorStoreMock;
