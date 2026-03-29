#pragma once

/**
 * Get user input is y ( Y/Yes ) or n ( N/No )
 * If user input an unmatched choice,this function will retry until user entered a legal choice.
 */
bool getInputBoolean();

/**
 * Get user input is y ( Y/Yes ) or n ( N/No )
 * If user input an unmatched choice,this function will return the default boolean value.
 */
bool getInputBoolean(bool def);

/**
 * Stuck until input 'enter'
 */
void parse();