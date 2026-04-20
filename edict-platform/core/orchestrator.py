"""
Edict Platform Core Orchestrator
基于 cft0808/edict “三省六部制”架构搭建的多智能体/流程调度引擎。
"""

import sys
import time

class Emperor:
    """皇上：系统的用户入口，负责下达最高指令"""
    @staticmethod
    def issue_edict(command: str):
        print(f"\n[皇上(User)] 下达旨意: {command}")
        return Triage.process(command)

class Triage:
    """太子：分拣意图"""
    @staticmethod
    def process(command: str):
        print("[太子(Triage)] 正在分拣意图...")
        time.sleep(0.5)
        if "聊天" in command or "闲聊" in command:
            return "太子批复：此为日常闲聊，无需惊动三省六部，儿臣代为回复即可。"
        else:
            print("[太子(Triage)] 此乃正式事务，转呈中书省规划。")
            return Planning.process(command)

class Planning:
    """中书省：规划拆解任务"""
    @staticmethod
    def process(command: str):
        print("[中书省(Planning)] 正在草拟多步执行计划...")
        time.sleep(1)
        plan = [
            f"1. 吏部查验权限与配置",
            f"2. 工部执行核心任务 ({command})",
            f"3. 礼部进行合规与文档记录"
        ]
        print("[中书省(Planning)] 计划草拟完毕。")
        return Review.process(plan)

class Review:
    """门下省：审议计划并卡控风险"""
    @staticmethod
    def process(plan: list):
        print("[门下省(Review)] 正在审议计划合规性...")
        time.sleep(1)
        # 模拟高风险审核
        print("[门下省(Review)] 计划符合《项目开发规范》，未发现高风险越权。予以通过！")
        return Dispatch.process(plan)

class Dispatch:
    """尚书省：调度六部并行执行"""
    @staticmethod
    def process(plan: list):
        print("[尚书省(Dispatch)] 奉天承运，开始派发任务至六部...")
        time.sleep(0.5)
        results = []
        for task in plan:
            if "吏部" in task:
                results.append(Ministry.execute("吏部", task))
            elif "户部" in task:
                results.append(Ministry.execute("户部", task))
            elif "礼部" in task:
                results.append(Ministry.execute("礼部", task))
            elif "兵部" in task:
                results.append(Ministry.execute("兵部", task))
            elif "刑部" in task:
                results.append(Ministry.execute("刑部", task))
            elif "工部" in task:
                results.append(Ministry.execute("工部", task))
        
        print("[尚书省(Dispatch)] 六部执行完毕，准备回奏！")
        return "\n".join(results)

class Ministry:
    """六部：实际干活的专业职能"""
    @staticmethod
    def execute(name: str, task: str):
        print(f"  -> [{name}] 正在执行: {task}")
        time.sleep(0.5)
        return f"{name} 完成奏报: {task} 执行成功"

if __name__ == "__main__":
    if len(sys.argv) > 1:
        command = " ".join(sys.argv[1:])
    else:
        command = "搭建一个新的核心结算模块"
    
    result = Emperor.issue_edict(command)
    print("\n========== 【回奏(Report)】 ==========")
    print(result)
