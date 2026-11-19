#!/usr/bin/env python3
"""Render the user guide's headings, paragraphs and lists as a four-page PDF.

Requires reportlab. Page comments in the Markdown define the printed sections.
"""
import argparse
from html import escape
from pathlib import Path
import re

from reportlab.lib.colors import HexColor
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import PageBreak, Paragraph, SimpleDocTemplate, Spacer


def inline(text):
    return re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", escape(text))


def render(source, output, font_dir):
    pdfmetrics.registerFont(TTFont("Guide", str(font_dir / "Arial.ttf")))
    pdfmetrics.registerFont(TTFont("GuideBold", str(font_dir / "Arial Bold.ttf")))
    pdfmetrics.registerFontFamily("Guide", normal="Guide", bold="GuideBold",
                                 italic="Guide", boldItalic="GuideBold")
    ink, teal, muted = map(HexColor, ("#17313b", "#067b78", "#536771"))
    styles = {
        "body": ParagraphStyle("body", fontName="Guide", fontSize=10.5, leading=14.4,
                               textColor=ink, spaceAfter=7),
        "title": ParagraphStyle("title", fontName="GuideBold", fontSize=23, leading=28,
                                textColor=ink, spaceAfter=16),
        "section": ParagraphStyle("section", fontName="GuideBold", fontSize=20, leading=25,
                                  textColor=teal, spaceAfter=13),
        "subsection": ParagraphStyle("subsection", fontName="GuideBold", fontSize=12,
                                     leading=16, textColor=ink, spaceBefore=9, spaceAfter=6),
        "item": ParagraphStyle("item", fontName="Guide", fontSize=10.5, leading=14.4,
                               textColor=ink, leftIndent=14, firstLineIndent=-14, spaceAfter=6),
    }
    text = re.sub(r"<!--(?! page -->).*?-->", "", source.read_text(), flags=re.S)
    sections = text.split("<!-- page -->")
    story = []
    for page, section in enumerate(sections):
        if page:
            story.append(PageBreak())
        blocks = re.split(r"\n\s*\n", section.strip())
        for block in blocks:
            lines = block.splitlines()
            if lines[0].startswith("# "):
                story.append(Paragraph(inline(lines[0][2:]), styles["title"]))
            elif lines[0].startswith("### "):
                story.append(Paragraph(inline(lines[0][4:]), styles["subsection"]))
            elif lines[0].startswith("## "):
                story.append(Paragraph(inline(lines[0][3:]), styles["section"]))
            elif re.match(r"(?:\d+\.|-) ", lines[0]):
                items = re.split(r"\n(?=(?:\d+\.|-) )", block)
                for item in items:
                    story.append(Paragraph(inline(" ".join(x.strip() for x in item.splitlines())), styles["item"]))
                story.append(Spacer(1, 3))
            else:
                story.append(Paragraph(inline(" ".join(x.strip() for x in lines)), styles["body"]))

    rendered_pages = []

    def frame(canvas, doc):
        rendered_pages.append(doc.page)
        width, height = A4
        canvas.saveState()
        canvas.setFillColor(teal)
        canvas.rect(0, height - 8, width, 8, fill=1, stroke=0)
        canvas.setFont("GuideBold", 8.5)
        canvas.drawString(43, height - 31, "TMM SENSOR PLANNER  /  ANVÄNDARGUIDE")
        canvas.setStrokeColor(HexColor("#d6e0e3"))
        canvas.line(43, 44, width - 43, 44)
        canvas.setFillColor(muted)
        canvas.setFont("Guide", 8)
        canvas.drawString(43, 29, "Uppdaterat gränssnitt efter kandidat 31  |  19 november 2026")
        canvas.drawRightString(width - 43, 29, f"{doc.page} / {len(sections)}")
        canvas.restoreState()

    output.parent.mkdir(parents=True, exist_ok=True)
    SimpleDocTemplate(str(output), pagesize=A4, rightMargin=43, leftMargin=43,
                      topMargin=48, bottomMargin=59, title="TMM Sensor Planner - användarguide",
                      author="TMM Sensor Planner").build(story, onFirstPage=frame, onLaterPages=frame)
    if len(rendered_pages) != len(sections):
        raise ValueError("Guide overflowed its intended pages; shorten content before delivery")


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=root / "docs/ANVANDARGUIDE.md")
    parser.add_argument("--output", type=Path, default=root / "output/pdf/tmm-sensor-planner-snabbguide.pdf")
    parser.add_argument("--font-dir", type=Path, default=Path("/System/Library/Fonts/Supplemental"))
    args = parser.parse_args()
    render(args.source, args.output, args.font_dir)
