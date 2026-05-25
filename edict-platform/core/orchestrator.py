"""
Edict Platform Core Orchestrator
基于 cft0808/edict “三省六部制”架构搭建的多智能体/流程调度引擎。
"""

import argparse
import sys
import time
from dataclasses import dataclass, field
from typing import List

from addressing import AddressingService, InteractionScene

@dataclass
class TaskSpec:
    ministry: str
    action: str
    payload: str
    display_text: str


@dataclass
class WorkflowContext:
    command: str
    plan: List[TaskSpec] = field(default_factory=list)
    approved: bool = False
    results: List[str] = field(default_factory=list)
    final_message: str = ""
    plan_mode: str = "default"
    review_mode: str = "approve"
    interaction_scene: InteractionScene = InteractionScene.MEMORIAL_REPLY
    planning_message: str = ""
    review_message: str = ""

class Emperor:
    """皇上：系统的用户入口，负责下达最高指令"""
    @staticmethod
    def issue_edict(
        command: str,
        plan_mode: str = "default",
        review_mode: str = "approve",
        interaction_scene: InteractionScene = InteractionScene.MEMORIAL_REPLY,
    ):
        print(f"\n[皇上(User)] 下达旨意: {command}")
        intent = Triage.process(command)
        if intent == "casual":
            return Triage.format_report(
                HanlinAcademy.compose_casual_reply(),
                "太子",
                interaction_scene,
            )

        workflow_context = WorkflowContext(
            command=command,
            plan_mode=plan_mode,
            review_mode=review_mode,
            interaction_scene=interaction_scene,
        )
        workflow_context = Planning.process(workflow_context)
        workflow_context = Review.process(workflow_context)
        if not workflow_context.approved:
            return workflow_context.final_message or Triage.format_report(
                "该计划未获批准，暂不施行。",
                "门下省",
                interaction_scene,
            )

        workflow_context = Dispatch.process(workflow_context)
        return workflow_context.final_message or "\n".join(workflow_context.results)

class Triage:
    """太子：分拣意图，编排报告格式"""
    @staticmethod
    def process(command: str):
        print("[太子(Triage)] 正在分拣意图...")
        time.sleep(0.5)
        if "聊天" in command or "闲聊" in command:
            return "casual"

        print("[太子(Triage)] 此乃正式事务，转呈中书省规划。")
        return "formal"

    @staticmethod
    def format_report(content: str, source: str, interaction_scene: InteractionScene) -> str:
        return AddressingService.format_message(source, interaction_scene, content)

class HanlinAcademy:
    """翰林院：汇总报告内容"""
    @staticmethod
    def compose_casual_reply() -> str:
        return "此为闲谈之事，无需惊动三省六部，臣即席答复。"

    @staticmethod
    def compose_rejection_report(workflow_context: WorkflowContext) -> str:
        return workflow_context.review_message

    @staticmethod
    def compose_execution_report(workflow_context: WorkflowContext) -> str:
        sections = [section for section in [workflow_context.planning_message, workflow_context.review_message] if section]
        sections.extend(workflow_context.results)
        return "六部奉诏办结，谨具施行结果如下。\n" + "\n".join(sections)

class Planning:
    """中书省：规划拆解任务"""
    @staticmethod
    def process(workflow_context: WorkflowContext) -> WorkflowContext:
        print("[中书省(Planning)] 正在草拟多步执行计划...")
        time.sleep(1)
        if workflow_context.plan_mode == "empty":
            workflow_context.plan = []
            workflow_context.planning_message = AddressingService.format_message(
                "中书省",
                workflow_context.interaction_scene,
                "奉诏检核后，今按演示场景具为空计划，请付门下省审覆。",
            )
            print("[中书省(Planning)] 已按演示参数生成空计划。")
            return workflow_context

        workflow_context.plan = [
            TaskSpec(ministry="吏部", action="validate", payload=workflow_context.command, display_text="1. 查验权限与配置"),
            TaskSpec(ministry="工部", action="execute", payload=workflow_context.command, display_text=f"2. 执行核心任务 ({workflow_context.command})"),
            TaskSpec(ministry="礼部", action="record", payload=workflow_context.command, display_text="3. 进行合规与文档记录")
        ]
        workflow_context.planning_message = AddressingService.format_message(
            "中书省",
            workflow_context.interaction_scene,
            "已据所奉旨意草拟三步施行之策，请付门下省审覆。",
        )
        print("[中书省(Planning)] 计划草拟完毕。")
        return workflow_context

class Review:
    """门下省：审议计划并卡控风险"""
    @staticmethod
    def process(workflow_context: WorkflowContext) -> WorkflowContext:
        print("[门下省(Review)] 正在审议计划合规性...")
        time.sleep(1)
        if not workflow_context.plan:
            workflow_context.approved = False
            workflow_context.review_message = AddressingService.format_message(
                "门下省",
                workflow_context.interaction_scene,
                "所呈计划为空，今已驳回，不予施行。",
            )
            workflow_context.final_message = HanlinAcademy.compose_rejection_report(workflow_context)
            print("[门下省(Review)] 未发现可执行任务，计划驳回。")
            return workflow_context

        if workflow_context.review_mode == "reject":
            workflow_context.approved = False
            workflow_context.review_message = AddressingService.format_message(
                "门下省",
                workflow_context.interaction_scene,
                "所呈计划存有风险，今已驳回，请另议施行之策。",
            )
            workflow_context.final_message = HanlinAcademy.compose_rejection_report(workflow_context)
            print("[门下省(Review)] 发现潜在风险，计划驳回。")
            return workflow_context

        print("[门下省(Review)] 计划符合《项目开发规范》，未发现高风险越权。予以通过！")
        workflow_context.approved = True
        workflow_context.review_message = AddressingService.format_message(
            "门下省",
            workflow_context.interaction_scene,
            "所呈计划审验无违，谨请付尚书省施行。",
        )
        return workflow_context

class Ministry:
    """六部：实际干活的专业职能"""
    @staticmethod
    def execute(name: str, task: str, scene: InteractionScene):
        print(f"  -> [{name}] 正在执行: {task}")
        time.sleep(0.5)
        return AddressingService.format_message(name, scene, f"{task} 已办结。")

TASK_REGISTRY = {
    ("吏部", "validate"): lambda task, scene: Ministry.execute("吏部", task.display_text, scene),
    ("户部", "validate"): lambda task, scene: Ministry.execute("户部", task.display_text, scene),
    ("礼部", "record"): lambda task, scene: Ministry.execute("礼部", task.display_text, scene),
    ("兵部", "execute"): lambda task, scene: Ministry.execute("兵部", task.display_text, scene),
    ("刑部", "validate"): lambda task, scene: Ministry.execute("刑部", task.display_text, scene),
    ("工部", "execute"): lambda task, scene: Ministry.execute("工部", task.payload, scene),
}


def dispatch_task(task: TaskSpec, scene: InteractionScene) -> str:
    handler = TASK_REGISTRY.get((task.ministry, task.action))
    if handler is None:
        raise ValueError(f"未注册的部门动作: {task.ministry}/{task.action}")
    return handler(task, scene)

class Dispatch:
    """尚书省：调度六部并行执行"""
    @staticmethod
    def process(workflow_context: WorkflowContext) -> WorkflowContext:
        print("[尚书省(Dispatch)] 奉天承运，开始派发任务至六部...")
        time.sleep(0.5)
        workflow_context.results = []
        for task in workflow_context.plan:
            workflow_context.results.append(dispatch_task(task, workflow_context.interaction_scene))
        
        print("[尚书省(Dispatch)] 六部执行完毕，准备回奏！")
        workflow_context.final_message = Triage.format_report(
            HanlinAcademy.compose_execution_report(workflow_context),
            "尚书省",
            workflow_context.interaction_scene,
        )
        return workflow_context


SCENE_ALIASES = {
    "memorial_reply": InteractionScene.MEMORIAL_REPLY,
    "official_document": InteractionScene.OFFICIAL_DOCUMENT,
    "court_discussion": InteractionScene.COURT_DISCUSSION,
}


def parse_args(argv: List[str]):
    parser = argparse.ArgumentParser(description="Edict Platform Core Orchestrator")
    parser.add_argument("command", nargs="*", help="要执行的旨意内容")
    parser.add_argument(
        "--plan-mode",
        choices=("default", "empty"),
        default="default",
        help="规划演示模式，empty 会生成空计划",
    )
    parser.add_argument(
        "--review-mode",
        choices=("approve", "reject"),
        default="approve",
        help="审议演示模式，reject 会在门下省驳回计划",
    )
    parser.add_argument(
        "--interaction-scene",
        choices=tuple(SCENE_ALIASES.keys()),
        default="memorial_reply",
        help="交互场景，控制称谓与行文格式",
    )
    return parser.parse_args(argv)

if __name__ == "__main__":
    args = parse_args(sys.argv[1:])
    command = " ".join(args.command) if args.command else "搭建一个新的核心结算模块"

    result = Emperor.issue_edict(
        command,
        plan_mode=args.plan_mode,
        review_mode=args.review_mode,
        interaction_scene=SCENE_ALIASES[args.interaction_scene],
    )
    print("\n========== 【回奏(Report)】 ==========")
    print(result)
