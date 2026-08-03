#ifndef OCTAVO_READER_JUSTIFICATION_H
#define OCTAVO_READER_JUSTIFICATION_H

#include "base/base_core.h"

/*
 * Platform-neutral 8vo host policy for positioning a bounded justified row.
 *
 * Reader0 remains authoritative for EPUB interpretation, line composition,
 * and the semantic row alignment. The caller supplies measured pixel values
 * and row traits from that authoritative frame. This module allocates no
 * storage, retains no pointers, and returns a complete value plan that any
 * 8vo renderer can consume without maintaining its own spacing policy.
 */
enum
{
  OctavoReaderJustificationRowByteCap = 4096,
};

typedef enum OctavoReaderJustificationBlockRole
{
  OctavoReaderJustificationBlockRole_Other,
  OctavoReaderJustificationBlockRole_Paragraph,
  OctavoReaderJustificationBlockRole_Blockquote,
} OctavoReaderJustificationBlockRole;

typedef struct OctavoReaderJustificationInput
{
  OctavoReaderJustificationBlockRole block_role;
  B32 publisher_justified;
  B32 block_last_row;
  B32 soft_wrapped;
  B32 safe_styles;
  U32 heading_level;
  U32 line_row;
  U32 row_byte_count;
  S32 margin_left_cols;
  S32 text_indent_cols;
  U32 internal_space_count;
  S32 natural_space_width_px;
  S32 natural_width_px;
  S32 available_width_px;
} OctavoReaderJustificationInput;

typedef struct OctavoReaderJustificationPlan
{
  B32 active;
  U32 space_count;
  S32 natural_width_px;
  S32 available_width_px;
  S32 applied_extra_px;
  S32 extra_per_space_px;
  U32 extra_remainder_px;
  S32 drawn_width_px;
} OctavoReaderJustificationPlan;

static S32
octavo_reader_justification_saturating_mul_nonnegative_s32(S32 value,
                                                            S32 multiplier)
{
  if (value <= 0 || multiplier <= 0) return 0;
  return (S32)MIN((S64)value * (S64)multiplier, (S64)INT32_MAX);
}

/*
 * Reader0 may retain a trailing space or tab as the semantic delimiter that
 * caused a soft wrap. Hosts omit only that layout delimiter. Hard rows,
 * including preformatted whitespace, retain their spaces; CR/LF bytes remain
 * non-drawing row separators in either case.
 */
static U32
octavo_reader_visible_row_end(const U8 *text,
                              U32 start,
                              U32 end,
                              B32 soft_wrapped)
{
  if (!text || end < start) return start;
  while (end > start &&
         (text[end - 1u] == '\r' || text[end - 1u] == '\n'))
  {
    end -= 1u;
  }
  while (soft_wrapped && end > start &&
         (text[end - 1u] == ' ' || text[end - 1u] == '\t'))
  {
    end -= 1u;
  }
  return end;
}

static S32
octavo_reader_justification_base_cap_per_space(U32 space_count,
                                                S32 natural_space_width_px)
{
  if (space_count == 0) return 0;
  S32 natural_space_w = MAX(natural_space_width_px, 1);
  S32 ratio_cap = 4;
  S32 absolute_cap = 4;
  if (space_count >= 7)
  {
    ratio_cap = octavo_reader_justification_saturating_mul_nonnegative_s32(
      natural_space_w, 2);
    absolute_cap = 12;
  }
  else if (space_count >= 5)
  {
    ratio_cap = octavo_reader_justification_saturating_mul_nonnegative_s32(
      natural_space_w, 3);
    absolute_cap = 14;
  }
  else if (space_count >= 3)
  {
    ratio_cap = natural_space_w;
    absolute_cap = 6;
  }
  return MAX(MIN(ratio_cap, absolute_cap), 0);
}

static S32
octavo_reader_justification_ceil_slack_per_space(S32 available_width_px,
                                                  S32 natural_width_px,
                                                  U32 space_count)
{
  if (space_count == 0 || available_width_px <= natural_width_px)
  {
    return 0;
  }
  S64 slack = (S64)available_width_px - natural_width_px;
  S64 count = (S64)space_count;
  S64 needed = (slack / count) + ((slack % count) != 0);
  return (S32)MIN(needed, (S64)INT32_MAX);
}

static B32
octavo_reader_justification_input_is_eligible(
  const OctavoReaderJustificationInput *input)
{
  if (!input) return 0;
  B32 paragraph = input->block_role ==
    OctavoReaderJustificationBlockRole_Paragraph;
  B32 blockquote = input->block_role ==
    OctavoReaderJustificationBlockRole_Blockquote;
  B32 kind_allows = input->publisher_justified && input->soft_wrapped &&
    (paragraph || blockquote);
  B32 margin_allows = input->margin_left_cols == 0 ||
    (blockquote && input->margin_left_cols <= 8);
  return kind_allows && !input->block_last_row &&
    input->heading_level == 0 && input->row_byte_count >= 8 &&
    margin_allows && input->safe_styles;
}

static S32
octavo_reader_justification_cap_per_space(
  const OctavoReaderJustificationInput *input)
{
  if (!input || input->internal_space_count == 0) return 0;
  U32 space_count = input->internal_space_count;
  S32 natural_space_w = MAX(input->natural_space_width_px, 1);
  S32 natural_w = input->natural_width_px;
  S32 available_w = input->available_width_px;
  B32 paragraph = input->block_role ==
    OctavoReaderJustificationBlockRole_Paragraph;
  B32 blockquote = input->block_role ==
    OctavoReaderJustificationBlockRole_Blockquote;
  S32 result = octavo_reader_justification_base_cap_per_space(
    space_count, natural_space_w);

  if (paragraph && input->heading_level == 0 &&
      input->publisher_justified && input->margin_left_cols == 0 &&
      input->text_indent_cols <= 4 &&
      (input->text_indent_cols <= 2 || input->line_row > 0 ||
       space_count <= 4) &&
      space_count >= 3 && natural_w > 0 && available_w > 0 &&
      (S64)natural_w * 10 >= (S64)available_w * 7)
  {
    S32 ratio_cap =
      octavo_reader_justification_saturating_mul_nonnegative_s32(
        natural_space_w,
        (input->text_indent_cols > 0 && space_count <= 4) ? 6 : 5);
    S32 absolute_cap = input->text_indent_cols == 0 ? 24 :
      (space_count <= 4 ? 34 : 28);
    result = MAX(result, MIN(ratio_cap, absolute_cap));
  }
  if (blockquote)
  {
    S32 ratio_cap =
      octavo_reader_justification_saturating_mul_nonnegative_s32(
        natural_space_w, space_count <= 4 ? 6 : 5);
    S32 absolute_cap = space_count <= 4 ? 28 : 24;
    result = MAX(result, MIN(ratio_cap, absolute_cap));
  }
  if (input->line_row == 0 && input->text_indent_cols > 0 &&
      space_count >= 4)
  {
    result = MAX(
      result,
      MIN(octavo_reader_justification_saturating_mul_nonnegative_s32(
            natural_space_w, 4),
          20));
  }

  B32 low_indent_fill = paragraph && input->heading_level == 0 &&
    input->publisher_justified && input->margin_left_cols == 0 &&
    input->text_indent_cols <= 4 &&
    (input->text_indent_cols <= 2 || input->line_row == 0) &&
    space_count >= 5 && natural_w > 0 && available_w > natural_w &&
    (S64)natural_w * 20 >= (S64)available_w * 13;
  B32 indented_fill = paragraph && input->heading_level == 0 &&
    input->publisher_justified && input->margin_left_cols == 0 &&
    input->text_indent_cols > 4 && input->text_indent_cols <= 8 &&
    input->line_row > 0 && space_count >= 5 && natural_w > 0 &&
    available_w > natural_w &&
    (S64)natural_w * 4 >= (S64)available_w * 3;
  if (low_indent_fill || indented_fill)
  {
    S32 needed = octavo_reader_justification_ceil_slack_per_space(
      available_w, natural_w, space_count);
    S32 fill_cap = MAX(
      octavo_reader_justification_saturating_mul_nonnegative_s32(
        natural_space_w, 8),
      40);
    if (indented_fill)
    {
      fill_cap = MIN(
        octavo_reader_justification_saturating_mul_nonnegative_s32(
          natural_space_w, 4),
        24);
    }
    if (needed <= fill_cap) result = MAX(result, needed);
  }
  /*
   * A non-final soft row with CSS publisher justification must reach its
   * declared measure. Leaving residual taste-cap slack visible produced
   * neither true justification nor intentionally ragged typography. The
   * resolver remains bounded by the caller's measured available width and
   * distributes only that finite slack.
   */
  B32 full_publisher_row = paragraph &&
    input->publisher_justified && natural_w > 0 &&
    available_w > natural_w;
  if (full_publisher_row)
  {
    S32 needed = octavo_reader_justification_ceil_slack_per_space(
      available_w, natural_w, space_count);
    result = MAX(result, needed);
  }
  return MAX(result, 0);
}

static B32
octavo_reader_justification_plan_resolve(
  const OctavoReaderJustificationInput *input,
  OctavoReaderJustificationPlan *out_plan)
{
  if (!out_plan) return 0;
  *out_plan = (OctavoReaderJustificationPlan){0};
  if (!input || input->row_byte_count > OctavoReaderJustificationRowByteCap ||
      input->internal_space_count > input->row_byte_count ||
      input->natural_space_width_px < 0 || input->natural_width_px < 0 ||
      input->available_width_px < 0)
  {
    return 0;
  }

  out_plan->space_count = input->internal_space_count;
  out_plan->natural_width_px = input->natural_width_px;
  out_plan->available_width_px = input->available_width_px;
  out_plan->drawn_width_px = input->natural_width_px;
  if (!octavo_reader_justification_input_is_eligible(input) ||
      input->internal_space_count == 0 ||
      input->available_width_px <= input->natural_width_px)
  {
    return 1;
  }

  S32 cap_per_space = octavo_reader_justification_cap_per_space(input);
  S64 slack = (S64)input->available_width_px - input->natural_width_px;
  S64 max_slack = (S64)input->internal_space_count * cap_per_space;
  S32 applied = (S32)MIN(slack, max_slack);
  if (applied <= 0) return 1;

  out_plan->active = 1;
  out_plan->applied_extra_px = applied;
  out_plan->extra_per_space_px =
    applied / (S32)input->internal_space_count;
  out_plan->extra_remainder_px =
    (U32)(applied % (S32)input->internal_space_count);
  out_plan->drawn_width_px = input->natural_width_px + applied;
  return 1;
}

static S32
octavo_reader_justification_offset_before_space_px(
  const OctavoReaderJustificationPlan *plan,
  U32 space_index)
{
  if (!plan || !plan->active || plan->space_count == 0 ||
      space_index > plan->space_count)
  {
    return 0;
  }
  U64 distributed_remainder =
    ((U64)space_index * plan->extra_remainder_px) / plan->space_count;
  return (S32)space_index * plan->extra_per_space_px +
    (S32)distributed_remainder;
}

static S32
octavo_reader_justification_space_extra_px(
  const OctavoReaderJustificationPlan *plan,
  U32 space_index)
{
  if (!plan || !plan->active || space_index >= plan->space_count) return 0;
  return octavo_reader_justification_offset_before_space_px(
           plan, space_index + 1) -
         octavo_reader_justification_offset_before_space_px(
           plan, space_index);
}

#endif /* OCTAVO_READER_JUSTIFICATION_H */
