/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.core.measure.db;

import com.google.common.base.Charsets;
import org.sonar.api.rules.RulePriority;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import java.util.Date;

import static org.sonar.api.utils.DateUtils.dateToLong;
import static org.sonar.api.utils.DateUtils.longToDate;

public class MeasureDto {
  private static final String INDEX_SHOULD_BE_IN_RANGE_FROM_1_TO_5 = "Index should be in range from 1 to 5";
  private static final int MAX_TEXT_VALUE_LENGTH = 4000;

  private Long id;
  private Double value;
  private String textValue;
  private Integer tendency;
  private byte[] measureData;
  private Double variation1, variation2, variation3, variation4, variation5;
  private Long measureDateMs;
  private String alertStatus;
  private String alertText;
  private String url;
  private String description;
  private Integer severityIndex;

  private Integer projectId;
  private Integer metricId;
  private Integer snapshotId;
  private Integer ruleId;
  private Integer characteristicId;
  private Integer personId;

  // TODO to delete – not in db
  private String metricKey;
  private String componentKey;

  public Long getId() {
    return id;
  }

  public MeasureDto setId(Long id) {
    this.id = id;
    return this;
  }

  @CheckForNull
  public Double getValue() {
    return value;
  }

  public MeasureDto setValue(@Nullable Double value) {
    this.value = value;
    return this;
  }

  @CheckForNull
  public String getData() {
    if (measureData != null) {
      return new String(measureData, Charsets.UTF_8);
    }
    return textValue;
  }

  public MeasureDto setData(String data) {
    if (data == null) {
      this.textValue = null;
      this.measureData = null;
    } else if (data.length() > MAX_TEXT_VALUE_LENGTH) {
      this.textValue = null;
      this.measureData = data.getBytes(Charsets.UTF_8);
    } else {
      this.textValue = data;
      this.measureData = null;
    }

    return this;
  }

  public Double getVariation(int index) {
    switch (index) {
      case 1:
        return variation1;
      case 2:
        return variation2;
      case 3:
        return variation3;
      case 4:
        return variation4;
      case 5:
        return variation5;
      default:
        throw new IndexOutOfBoundsException(INDEX_SHOULD_BE_IN_RANGE_FROM_1_TO_5);
    }
  }

  public MeasureDto setVariation(int index, Double d) {
    switch (index) {
      case 1:
        variation1 = d;
        break;
      case 2:
        variation2 = d;
        break;
      case 3:
        variation3 = d;
        break;
      case 4:
        variation4 = d;
        break;
      case 5:
        variation5 = d;
        break;
      default:
        throw new IndexOutOfBoundsException(INDEX_SHOULD_BE_IN_RANGE_FROM_1_TO_5);
    }
    return this;
  }

  public Integer getTendency() {
    return tendency;
  }

  public MeasureDto setTendency(Integer tendency) {
    this.tendency = tendency;
    return this;
  }

  public Date getDate() {
    return longToDate(measureDateMs);
  }

  public MeasureDto setDate(Date date) {
    this.measureDateMs = dateToLong(date);
    return this;
  }

  public String getAlertStatus() {
    return alertStatus;
  }

  public MeasureDto setAlertStatus(String alertStatus) {
    this.alertStatus = alertStatus;
    return this;
  }

  public String getAlertText() {
    return alertText;
  }

  public MeasureDto setAlertText(String alertText) {
    this.alertText = alertText;
    return this;
  }

  public String getUrl() {
    return url;
  }

  public MeasureDto setUrl(String url) {
    this.url = url;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public MeasureDto setDescription(String description) {
    this.description = description;
    return this;
  }

  public RulePriority getSeverity() {
    return RulePriority.valueOfInt(severityIndex);
  }

  public MeasureDto setSeverity(RulePriority severity) {
    this.severityIndex = severity.ordinal();
    return this;
  }

  public Integer getProjectId() {
    return projectId;
  }

  public MeasureDto setProjectId(Integer projectId) {
    this.projectId = projectId;
    return this;
  }

  public Integer getMetricId() {
    return metricId;
  }

  public MeasureDto setMetricId(Integer metricId) {
    this.metricId = metricId;
    return this;
  }

  public Integer getSnapshotId() {
    return snapshotId;
  }

  public MeasureDto setSnapshotId(Integer snapshotId) {
    this.snapshotId = snapshotId;
    return this;
  }

  public Integer getRuleId() {
    return ruleId;
  }

  public MeasureDto setRuleId(Integer ruleId) {
    this.ruleId = ruleId;
    return this;
  }

  public Integer getCharacteristicId() {
    return characteristicId;
  }

  public MeasureDto setCharacteristicId(Integer characteristicId) {
    this.characteristicId = characteristicId;
    return this;
  }

  public Integer getPersonId() {
    return personId;
  }

  public MeasureDto setPersonId(Integer personId) {
    this.personId = personId;
    return this;
  }

  public String getMetricKey() {
    return metricKey;
  }

  public MeasureDto setMetricKey(String metricKey) {
    this.metricKey = metricKey;
    return this;
  }

  public String getComponentKey() {
    return componentKey;
  }

  public MeasureDto setComponentKey(String componentKey) {
    this.componentKey = componentKey;
    return this;
  }
}
