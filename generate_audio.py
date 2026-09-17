#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
题库预录音频批量生成脚本

命名规则：
  题目：q_{chapterId}_{levelIndex}.mp3
  选项：q_{chapterId}_{levelIndex}_{optionIndex}.mp3    （optionIndex: 1~4）
  UI：ui_{scene}.mp3
  选项索引：option_index.json

option_index.json 结构：
  {
    "1_1": { "wǒ": "q_1_1_1.mp3", "wō": "q_1_1_2.mp3", ... },
    ...
  }

用途：Java 端选项顺序会被 Collections.shuffle 打乱，无法用索引直接拼
文件名。改为用"选项文本"查表，得到正确的 MP3 文件名。

用法：
  # 生成全部
  python generate_audio.py

  # 只生成第 1 章
  python generate_audio.py --chapter 1

  # 只生成第 1、3、5 章
  python generate_audio.py --chapter 1,3,5

  # 只生成第 1 章第 5 关
  python generate_audio.py --chapter 1 --level 5

  # 只生成第 1 章的第 5、6、7 关
  python generate_audio.py --chapter 1 --level 5,6,7

  # ★ 只生成题干 + 正确答案（跳过 3 个干扰项）
  python generate_audio.py --only-correct

  # ★ 只生成第 1 章第 5 关的题干 + 正确答案
  python generate_audio.py --chapter 1 --level 5 --only-correct

  # 预览要生成什么，不实际调用 TTS
  python generate_audio.py --only-correct --dry-run

  # 只生成 UI
  python generate_audio.py --skip-questions

  # 强制重新生成
  python generate_audio.py --force
"""

import asyncio
import json
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

# ★ 源文件格式约定：每个题目行的第 2 列（index=2）永远是正确答案。
#    见 ChapterDataSource.createChoiceQuestion / createListenQuestion：
#    options.add(correctAnswer) 在最前，之后才 addAll(wrongAnswers)。
#    所以 Python 侧 opt_idx == 1 恒对应正确答案。
CORRECT_OPTION_INDEX = 1

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


def parse_csv_ints(s):
    """解析 '1,3,5' 形式为 [1,3,5]；空字符串返回 None"""
    if s is None:
        return None
    s = s.strip()
    if not s:
        return None
    result = set()
    for part in s.split(","):
        part = part.strip()
        if not part:
            continue
        try:
            result.add(int(part))
        except ValueError:
            print(f"❌ 无法解析数字：{part!r}")
            sys.exit(1)
    return sorted(result)


def unescape_java_string(s: str) -> str:
    """把 Java 字符串字面量还原为'适合 TTS 朗读'的文本（\\n → 空格）"""
    result = []
    i, n = 0, len(s)
    while i < n:
        c = s[i]
        if c == '\\' and i + 1 < n:
            nxt = s[i + 1]
            if nxt in ('n', 't', 'r'):
                result.append(' ')
                i += 2
            elif nxt == '"':
                result.append('"'); i += 2
            elif nxt == "'":
                result.append("'"); i += 2
            elif nxt == '\\':
                result.append('\\'); i += 2
            elif nxt == 'u' and i + 5 < n:
                try:
                    result.append(chr(int(s[i + 2:i + 6], 16)))
                    i += 6
                except ValueError:
                    result.append(c); i += 1
            else:
                result.append(nxt); i += 2
        else:
            result.append(c); i += 1
    return ''.join(result)


def unescape_java_string_exact(s: str) -> str:
    """
    精确还原 Java 字符串字面量的运行时值（用于 JSON key）。
    与 Java 编译后 options.get(index) 拿到的字符串完全一致。
    """
    result = []
    i, n = 0, len(s)
    while i < n:
        c = s[i]
        if c == '\\' and i + 1 < n:
            nxt = s[i + 1]
            if nxt == 'n':
                result.append('\n'); i += 2
            elif nxt == 't':
                result.append('\t'); i += 2
            elif nxt == 'r':
                result.append('\r'); i += 2
            elif nxt == '"':
                result.append('"'); i += 2
            elif nxt == "'":
                result.append("'"); i += 2
            elif nxt == '\\':
                result.append('\\'); i += 2
            elif nxt == 'u' and i + 5 < n:
                try:
                    result.append(chr(int(s[i + 2:i + 6], 16)))
                    i += 6
                except ValueError:
                    result.append(c); i += 1
            else:
                result.append(nxt); i += 2
        else:
            result.append(c); i += 1
    return ''.join(result)


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
      questions:    { (chapterId, levelIndex): "题干" }
      options:      { (chapterId, levelIndex): ["opt1",...] }  # 清洗后（用于 TTS）
      option_index: { "chapterId_levelIndex": { "原始选项文本": "q_x_y_N.mp3" } }
    """
    if not java_path.exists():
        print(f"❌ 找不到 Java 文件：{java_path}")
        sys.exit(1)

    text = java_path.read_text(encoding="utf-8")

    chapter_decl_pattern = re.compile(
        r'(?:private|public|protected)\s+static\s+Chapter\s+createChapter(\d+)\s*\('
    )

    row_pattern = re.compile(
        r'\{\s*"(语文|数学|听力)"\s*,\s*'
        r'"((?:[^"\\]|\\.)*)"\s*,\s*'  # 题干
        r'"((?:[^"\\]|\\.)*)"\s*,\s*'  # 选项1（= 正确答案）
        r'"((?:[^"\\]|\\.)*)"\s*,\s*'  # 选项2
        r'"((?:[^"\\]|\\.)*)"\s*,\s*'  # 选项3
        r'"((?:[^"\\]|\\.)*)"\s*\}',   # 选项4
        re.MULTILINE
    )

    chapter_markers = [(m.start(), int(m.group(1))) for m in chapter_decl_pattern.finditer(text)]
    chapter_markers.sort(key=lambda x: x[0])

    if not chapter_markers:
        print("⚠ 未找到任何 createChapterN() 方法声明")
        return {}, {}, {}

    questions = {}
    options = {}
    option_index = {}
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

        # 题干（用于 TTS）
        speak_text = unescape_java_string(raw_prompt)
        speak_text = clean_text(speak_text, q_type)
        if q_type == "听力":
            speak_text = speak_text + "，" + speak_text
        questions[key] = speak_text

        # 选项（用于 TTS，保留原始顺序）
        opts = []
        for i in range(3, 7):
            cleaned = clean_option(unescape_java_string(m.group(i)))
            opts.append(cleaned)
        options[key] = opts

        # 选项索引：原始文本（Java 运行时值） -> 文件名
        key_str = f"{chapter}_{current_level_index}"
        option_index[key_str] = {}
        for i in range(3, 7):
            exact_text = unescape_java_string_exact(m.group(i))
            filename = f"q_{chapter}_{current_level_index}_{i - 2}.mp3"
            if exact_text in option_index[key_str]:
                print(f"⚠ 第 {chapter} 章第 {current_level_index} 关选项文本重复: {exact_text!r}")
            option_index[key_str][exact_text] = filename

    return questions, options, option_index


def write_option_index(output_dir: Path, option_index: dict, dry_run: bool = False):
    """写入 option_index.json"""
    json_path = output_dir / "option_index.json"
    if dry_run:
        total = sum(len(v) for v in option_index.values())
        print(f"📝 [dry-run] 将写入 {json_path}：{len(option_index)} 题 / {total} 选项")
        return
    try:
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(option_index, f, ensure_ascii=False, indent=2, sort_keys=True)
        total = sum(len(v) for v in option_index.values())
        print(f"📝 已生成选项索引：{json_path}")
        print(f"   包含 {len(option_index)} 道题的 {total} 个选项")
    except Exception as e:
        print(f"❌ 写入 option_index.json 失败: {e}")


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
    """
    根据 --chapter / --level 过滤。
    返回 (questions, options, is_single_mode)
    """
    chapters = parse_csv_ints(args.chapter)
    levels = parse_csv_ints(args.level)

    if levels is not None:
        if chapters is None:
            print("❌ 使用 --level 时必须同时指定 --chapter")
            sys.exit(1)
        if len(chapters) != 1:
            print("❌ 使用 --level 时 --chapter 只能指定一个章节（如 --chapter 1 --level 5,6,7）")
            sys.exit(1)

    # 检查章节存在性
    if chapters is not None:
        all_chapters = {c for (c, _) in questions.keys()}
        for c in chapters:
            if c not in all_chapters:
                print(f"❌ 找不到第 {c} 章")
                print(f"   可用章节：{sorted(all_chapters)}")
                sys.exit(1)

    # 检查关卡存在性
    if levels is not None and chapters is not None:
        chapter = chapters[0]
        available = sorted([l for (c, l) in questions.keys() if c == chapter])
        for lv in levels:
            if lv not in available:
                print(f"❌ 找不到第 {chapter} 章第 {lv} 关")
                print(f"   第 {chapter} 章可用关卡：{available[0]} ~ {available[-1]}")
                sys.exit(1)

    # 应用过滤
    if chapters is not None:
        questions = {k: v for k, v in questions.items() if k[0] in chapters}
        options = {k: v for k, v in options.items() if k[0] in chapters}
    if levels is not None:
        chapter = chapters[0]
        questions = {k: v for k, v in questions.items() if k[0] == chapter and k[1] in levels}
        options = {k: v for k, v in options.items() if k[0] == chapter and k[1] in levels}

    is_single_mode = (len(questions) == 1)
    return questions, options, is_single_mode


# ---------- 主流程 ----------

async def main_async(args):
    java_path = Path(args.java).resolve()
    output_dir = Path(args.output).resolve()
    if not args.dry_run:
        output_dir.mkdir(parents=True, exist_ok=True)

    print(f"📖 解析题库：{java_path}")
    questions, options, option_index = parse_java_file(java_path)
    print(f"   共解析出 {len(questions)} 道题")

    if not questions:
        print("❌ 未解析到任何题目")
        return

    # ★ option_index.json 无条件全量生成（不受 --chapter 过滤，也不受 --only-correct 影响）
    write_option_index(output_dir, option_index, dry_run=args.dry_run)

    chapter_counts = {}
    for (cid, _) in questions.keys():
        chapter_counts[cid] = chapter_counts.get(cid, 0) + 1
    print("   各章题数：", end="")
    for cid in sorted(chapter_counts.keys()):
        print(f"第{cid}章={chapter_counts[cid]}", end="  ")
    print()

    questions, options, is_single_mode = filter_data(questions, options, args)

    # 打印模式
    mode_parts = []
    if args.chapter:
        mode_parts.append(f"章节={args.chapter}")
    if args.level:
        mode_parts.append(f"关卡={args.level}")
    if args.only_correct:
        mode_parts.append("仅题干+答案")
    if args.skip_questions:
        mode_parts.append("跳过题目")
    if args.dry_run:
        mode_parts.append("预览模式")
    if mode_parts:
        print(f"🎯 模式：{' / '.join(mode_parts)}")

    print(f"\n🎙  音色：{args.voice}")
    print(f"🎚  语速：{args.rate}")
    print(f"📁 输出：{output_dir}\n")

    if is_single_mode:
        print(f"🎯 精确单条模式：{list(questions.keys())[0]}\n")

    success, skipped, failed = 0, 0, 0

    do_questions = not args.skip_questions
    do_ui = not is_single_mode

    # ========== 1. 生成题目 MP3 ==========
    if do_questions and questions:
        print(f"--- 题目语音 ---")
        total = len(questions)
        for idx, ((chapter, level), text) in enumerate(sorted(questions.items()), 1):
            filename = f"q_{chapter}_{level}.mp3"
            out_file = output_dir / filename
            prefix = f"[{idx}/{total}]"

            if out_file.exists() and not args.force and not args.dry_run:
                print(f"{prefix} ⏭  {filename}（已存在）")
                skipped += 1
                continue

            safe_show = text.replace('\\', '')
            suffix = "（已存在）" if (out_file.exists() and not args.force) else ""
            print(f"{prefix} 🎬 {filename}{suffix}：{safe_show[:40]}{'...' if len(safe_show) > 40 else ''}")

            if args.dry_run:
                continue

            ok = await generate_one(text, out_file, args.voice, args.rate, args.volume)
            if ok: success += 1
            else: failed += 1

    # ========== 2. 生成选项 MP3 ==========
    if do_questions and options:
        # ★ --only-correct：只处理正确答案（opt_idx == 1）
        if args.only_correct:
            print(f"\n--- 选项语音（仅正确答案 q_x_y_1.mp3） ---")
        else:
            # ⚠ 注意：不要在这里使用 {中文} 形式的占位符
            # Python f-string 会把中文当合法标识符解析，导致 NameError
            print(f"\n--- 选项语音（q_章节_关卡_选项序号.mp3） ---")

        # 计算要生成的总数
        total = 0
        for (chapter, level), opts in options.items():
            if args.only_correct:
                total += 1
            else:
                total += len(opts)

        idx = 0
        for (chapter, level), opts in sorted(options.items()):
            for opt_idx, opt_text in enumerate(opts, 1):
                # ★ 跳过非正确答案的选项
                if args.only_correct and opt_idx != CORRECT_OPTION_INDEX:
                    continue

                idx += 1
                filename = f"q_{chapter}_{level}_{opt_idx}.mp3"
                out_file = output_dir / filename
                prefix = f"[{idx}/{total}]"

                if out_file.exists() and not args.force and not args.dry_run:
                    print(f"{prefix} ⏭  {filename}（已存在）：{opt_text}")
                    skipped += 1
                    continue

                suffix = "（已存在）" if (out_file.exists() and not args.force) else ""
                print(f"{prefix} 🎬 {filename}{suffix}：{opt_text}")

                if args.dry_run:
                    continue

                ok = await generate_one(opt_text, out_file, args.voice, args.rate, args.volume)
                if ok: success += 1
                else: failed += 1

    # ========== 3. 生成 UI 场景 MP3 ==========
    if do_ui:
        print(f"\n--- UI 场景语音 ---")
        for scene, text in UI_SCENES.items():
            filename = f"ui_{scene}.mp3"
            out_file = output_dir / filename
            if out_file.exists() and not args.force and not args.dry_run:
                print(f"⏭  {filename}（已存在）")
                skipped += 1
                continue
            suffix = "（已存在）" if (out_file.exists() and not args.force) else ""
            print(f"🎬 {filename}{suffix}：{text}")
            if args.dry_run:
                continue
            ok = await generate_one(text, out_file, args.voice, args.rate, args.volume)
            if ok: success += 1
            else: failed += 1

    print(f"\n{'=' * 50}")
    if args.dry_run:
        print(f"🔎 预览模式：未实际生成任何文件")
    else:
        print(f"✅ 成功：{success}")
        print(f"⏭  跳过：{skipped}")
        print(f"❌ 失败：{failed}")
    print(f"📁 输出目录：{output_dir}")
    print(f"{'=' * 50}")


def main():
    parser = argparse.ArgumentParser(
        description="题库预录音频批量生成",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例：
  python generate_audio.py                               # 全部
  python generate_audio.py --chapter 1                   # 第 1 章
  python generate_audio.py --chapter 1,3,5               # 第 1、3、5 章
  python generate_audio.py --chapter 1 --level 5         # 第 1 章第 5 关
  python generate_audio.py --chapter 1 --level 5,6,7     # 第 1 章的第 5、6、7 关
  python generate_audio.py --only-correct                # 只生成题干 + 正确答案
  python generate_audio.py --chapter 1 --level 5 --only-correct   # 指定题目+答案
  python generate_audio.py --only-correct --dry-run      # 预览
  python generate_audio.py --skip-questions              # 只生成 UI
  python generate_audio.py --force                       # 强制重新生成
        """
    )
    parser.add_argument("--java", default=JAVA_FILE, help="Java 题库文件路径")
    parser.add_argument("--output", default=DEFAULT_OUTPUT, help="MP3 输出目录")
    parser.add_argument("--voice", default=DEFAULT_VOICE, help="音色名称")
    parser.add_argument("--rate", default=DEFAULT_RATE, help="语速调整，如 -20%%")
    parser.add_argument("--volume", default=DEFAULT_VOLUME, help="音量调整，如 +0%%")
    parser.add_argument("--chapter", type=str, default=None,
                        help="只生成指定章节，可多个用逗号分隔，如 1,3,5")
    parser.add_argument("--level", type=str, default=None,
                        help="只生成指定关卡（需配合单章 --chapter），如 1,3,5")
    parser.add_argument("--only-correct", action="store_true",
                        help="只生成题干 MP3 + 正确答案选项 MP3（跳过干扰项）")
    parser.add_argument("--skip-questions", action="store_true",
                        help="跳过题目和选项，只生成 UI")
    parser.add_argument("--dry-run", action="store_true",
                        help="只打印要生成的内容，不实际调用 TTS")
    parser.add_argument("--force", action="store_true", help="强制重新生成")
    args = parser.parse_args()

    asyncio.run(main_async(args))


if __name__ == "__main__":
    main()