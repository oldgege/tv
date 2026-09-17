#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
题库预录音频批量生成脚本

命名规则：
  题目：q_{chapterId}_{levelIndex}.mp3
  选项：q_{chapterId}_{levelIndex}_{optionIndex}.mp3    （optionIndex: 1~4）
  UI：ui_{scene}.mp3

用法：
  python generate_audio.py
  python generate_audio.py --chapter 1
  python generate_audio.py --chapter 1 --level 5
  python generate_audio.py --skip-questions   # 只生成 UI
  python generate_audio.py --force
"""

import asyncio
import os
import re
import sys
import argparse
from pathlib import Path

try:
    import edge_tts
except ImportError:
    print("❌ 缺少依赖：请先运行  pip install edge-tts")
    sys.exit(1)


# ========== 配置区 ==========

JAVA_FILE = "app/src/main/java/com/wengyj/tv/data/datasource/ChapterDataSource.java"
DEFAULT_OUTPUT = "app/src/main/assets/audio"
DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural"
DEFAULT_RATE = "-20%"
DEFAULT_VOLUME = "+0%"
MAX_RETRY = 3

UI_SCENES = {
    "grade_menu":     "请选择年级",
    "chapter_menu":   "请选择章节",
    "level_menu":     "请选择关卡",
    "not_unlocked":   "还未解锁哟",
    "chapter_locked": "请先完成上一章",
    "coming_soon":    "即将上线",
    "all_complete":   "恭喜你完成所有关卡",
    "stars_empty":    "星星用完了，本章重新开始吧",
    "option_tip":     "请选择",
}

# =================================


def unescape_java_string(s: str) -> str:
    s = s.replace('\\n', ' ')
    s = s.replace('\\t', ' ')
    s = s.replace('\\r', ' ')
    s = s.replace('\\"', '"')
    s = s.replace("\\'", "'")
    s = s.replace('\\', '')
    return s


def clean_text(text: str, q_type: str) -> str:
    text = text.replace('\n', ' ')
    text = text.replace('\u201C', '').replace('\u201D', '')
    text = text.replace('\u2018', '').replace('\u2019', '')
    text = text.replace('"', '').replace('"', '')

    if q_type == "数学":
        text = re.sub(r'（\s*）', '和', text)
        text = text.replace('比大小', '比较大小')
        text = text.replace('= ?', '等于几')
        text = text.replace('?', '？')

    text = text.rstrip('：:')
    text = re.sub(r'\s+', ' ', text).strip()
    text = text.replace('\\', '')
    return text


def clean_option(text: str) -> str:
    t = text.strip()
    t = t.strip('\u201C\u201D\u2018\u2019')
    t = t.strip('"').strip('"')

    if t == '>':
        return '大于'
    if t == '<':
        return '小于'
    if t == '=':
        return '等于'
    if t == '≠':
        return '不等于'

    return t


# ---------- 解析 Java 文件 ----------

def parse_java_file(java_path: Path):
    """
    解析 Java 文件。
    返回:
      questions: { (chapterId, levelIndex): "题干" }
      options:   { (chapterId, levelIndex): ["opt1", "opt2", "opt3", "opt4"] }
    """
    if not java_path.exists():
        print(f"❌ 找不到 Java 文件：{java_path}")
        sys.exit(1)

    text = java_path.read_text(encoding="utf-8")

    chapter_decl_pattern = re.compile(
        r'(?:private|public|protected)\s+static\s+Chapter\s+createChapter(\d+)\s*\('
    )

    # 匹配整行：{"类型", "题干", "选项1", "选项2", "选项3", "选项4"}
    row_pattern = re.compile(
        r'\{\s*"(语文|数学|听力)"\s*,\s*'
        r'"((?:[^"\\]|\\.)*)"\s*,\s*'  # 题干
        r'"((?:[^"\\]|\\.)*)"\s*,\s*'  # 选项1
        r'"((?:[^"\\]|\\.)*)"\s*,\s*'  # 选项2
        r'"((?:[^"\\]|\\.)*)"\s*,\s*'  # 选项3
        r'"((?:[^"\\]|\\.)*)"\s*\}',   # 选项4
        re.MULTILINE
    )

    chapter_markers = [(m.start(), int(m.group(1))) for m in chapter_decl_pattern.finditer(text)]
    chapter_markers.sort(key=lambda x: x[0])

    if not chapter_markers:
        print("⚠ 未找到任何 createChapterN() 方法声明")
        return {}, {}

    questions = {}
    options = {}
    current_chapter = None
    current_level_index = 0

    for m in row_pattern.finditer(text):
        q_type = m.group(1)
        raw_prompt = m.group(2)
        pos = m.start()

        chapter = None
        for marker_pos, chapter_id in chapter_markers:
            if marker_pos < pos:
                chapter = chapter_id
            else:
                break

        if chapter is None:
            continue

        if chapter != current_chapter:
            current_chapter = chapter
            current_level_index = 0

        current_level_index += 1
        key = (chapter, current_level_index)

        # 题干
        speak_text = unescape_java_string(raw_prompt)
        speak_text = clean_text(speak_text, q_type)
        if q_type == "听力":
            speak_text = speak_text + "，" + speak_text
        questions[key] = speak_text

        # 选项（保留原始顺序，不打乱）
        opts = []
        for i in range(3, 7):
            raw_opt = m.group(i)
            cleaned = clean_option(unescape_java_string(raw_opt))
            opts.append(cleaned)
        options[key] = opts

    return questions, options


# ---------- 音频生成 ----------

async def generate_one(text: str, output_file: Path, voice: str, rate: str, volume: str):
    for attempt in range(1, MAX_RETRY + 1):
        try:
            communicate = edge_tts.Communicate(text, voice, rate=rate, volume=volume)
            await communicate.save(str(output_file))
            return True
        except Exception as e:
            if attempt < MAX_RETRY:
                print(f"  ⚠ 第 {attempt} 次失败，2 秒后重试：{e}")
                await asyncio.sleep(2)
            else:
                print(f"  ❌ 生成失败（已重试 {MAX_RETRY} 次）：{e}")
                return False


# ---------- 过滤 ----------

def filter_data(questions, options, args):
    if args.level is not None and args.chapter is None:
        print("❌ 使用 --level 时必须同时指定 --chapter")
        sys.exit(1)

    if args.chapter is not None and args.level is not None:
        key = (args.chapter, args.level)
        if key not in questions:
            print(f"❌ 找不到第 {args.chapter} 章 第 {args.level} 关的题目")
            available = sorted([l for (c, l) in questions.keys() if c == args.chapter])
            if available:
                print(f"   第 {args.chapter} 章可用的关卡序号：{available[0]} ~ {available[-1]}")
            sys.exit(1)
        return {key: questions[key]}, {key: options[key]}, True

    if args.chapter is not None:
        questions = {k: v for k, v in questions.items() if k[0] == args.chapter}
        options = {k: v for k, v in options.items() if k[0] == args.chapter}
        print(f"   仅生成第 {args.chapter} 章：{len(questions)} 道题")

    return questions, options, False


# ---------- 主流程 ----------

async def main_async(args):
    java_path = Path(args.java).resolve()
    output_dir = Path(args.output).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"📖 解析题库：{java_path}")
    questions, options = parse_java_file(java_path)
    print(f"   共解析出 {len(questions)} 道题")

    if not questions:
        print("❌ 未解析到任何题目")
        return

    chapter_counts = {}
    for (cid, _) in questions.keys():
        chapter_counts[cid] = chapter_counts.get(cid, 0) + 1
    print("   各章题数：", end="")
    for cid in sorted(chapter_counts.keys()):
        print(f"第{cid}章={chapter_counts[cid]}", end="  ")
    print()

    questions, options, is_single_mode = filter_data(questions, options, args)

    print(f"\n🎙  音色：{args.voice}")
    print(f"🎚  语速：{args.rate}")
    print(f"📁 输出：{output_dir}\n")

    if is_single_mode:
        print(f"🎯 精确单条模式：第 {args.chapter} 章 第 {args.level} 关\n")

    success, skipped, failed = 0, 0, 0

    do_questions = not args.skip_questions
    do_ui = not is_single_mode and not args.skip_questions

    # ========== 1. 生成题目 MP3 ==========
    if do_questions and questions:
        print(f"--- 题目语音 ---")
        total = len(questions)
        for idx, ((chapter, level), text) in enumerate(sorted(questions.items()), 1):
            filename = f"q_{chapter}_{level}.mp3"
            out_file = output_dir / filename
            prefix = f"[{idx}/{total}]"

            if out_file.exists() and not args.force:
                print(f"{prefix} ⏭  {filename}（已存在）")
                skipped += 1
                continue

            safe_show = text.replace('\\', '')
            print(f"{prefix} 🎬 {filename}：{safe_show[:40]}{'...' if len(safe_show) > 40 else ''}")

            ok = await generate_one(text, out_file, args.voice, args.rate, args.volume)
            if ok:
                success += 1
            else:
                failed += 1

    # ========== 2. 生成选项 MP3 ==========
    if do_questions and options:
        print(f"\n--- 选项语音（q_{章节}_{关卡}_{选项序号}.mp3） ---")
        # 先计算总数
        total = sum(len(opts) for opts in options.values())
        idx = 0
        for (chapter, level), opts in sorted(options.items()):
            for opt_idx, opt_text in enumerate(opts, 1):
                idx += 1
                filename = f"q_{chapter}_{level}_{opt_idx}.mp3"
                out_file = output_dir / filename
                prefix = f"[{idx}/{total}]"

                if out_file.exists() and not args.force:
                    print(f"{prefix} ⏭  {filename}（已存在）：{opt_text}")
                    skipped += 1
                    continue

                print(f"{prefix} 🎬 {filename}：{opt_text}")
                ok = await generate_one(opt_text, out_file, args.voice, args.rate, args.volume)
                if ok:
                    success += 1
                else:
                    failed += 1

    # ========== 3. 生成 UI 场景 MP3 ==========
    if do_ui:
        print(f"\n--- UI 场景语音 ---")
        for scene, text in UI_SCENES.items():
            filename = f"ui_{scene}.mp3"
            out_file = output_dir / filename
            if out_file.exists() and not args.force:
                print(f"⏭  {filename}（已存在）")
                skipped += 1
                continue
            print(f"🎬 {filename}：{text}")
            ok = await generate_one(text, out_file, args.voice, args.rate, args.volume)
            if ok:
                success += 1
            else:
                failed += 1

    print(f"\n{'=' * 50}")
    print(f"✅ 成功：{success}")
    print(f"⏭  跳过：{skipped}")
    print(f"❌ 失败：{failed}")
    print(f"📁 输出目录：{output_dir}")
    print(f"{'=' * 50}")


def main():
    parser = argparse.ArgumentParser(description="题库预录音频批量生成")
    parser.add_argument("--java", default=JAVA_FILE, help="Java 题库文件路径")
    parser.add_argument("--output", default=DEFAULT_OUTPUT, help="MP3 输出目录")
    parser.add_argument("--voice", default=DEFAULT_VOICE, help="音色名称")
    parser.add_argument("--rate", default=DEFAULT_RATE, help="语速调整，如 -20%%")
    parser.add_argument("--volume", default=DEFAULT_VOLUME, help="音量调整，如 +0%%")
    parser.add_argument("--chapter", type=int, default=None, help="只生成指定章节")
    parser.add_argument("--level", type=int, default=None, help="只生成指定关卡（需配合 --chapter）")
    parser.add_argument("--skip-questions", action="store_true", help="跳过题目和选项，只生成 UI")
    parser.add_argument("--force", action="store_true", help="强制重新生成")
    args = parser.parse_args()

    asyncio.run(main_async(args))


if __name__ == "__main__":
    main()